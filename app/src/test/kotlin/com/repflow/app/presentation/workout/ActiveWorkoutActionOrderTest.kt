package com.repflow.app.presentation.workout

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditWorkoutSet
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.application.workout.WorkoutPersistenceError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * fix-workout-actions-in-order: workout actions run one after another, in the
 * order they were pressed. Each use case reads the whole session and writes it
 * back, so during a restore, when reads return the old data and writes wait,
 * two overlapping actions both read the old session and the second write undoes
 * the first. [InMemoryWorkoutRepository.holdWrites] stands in for the restore.
 *
 * The real use cases run over the fake, and every test lets the actions reach
 * their writes before releasing them, because with nothing waiting nothing
 * overlaps.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutActionOrderTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val dayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock)
    private val viewModel by lazy { newViewModel() }

    private fun newViewModel() =
        ActiveWorkoutViewModel(
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workoutRepository),
            observeExercises = ObserveExercises(exerciseRepository),
            observeTrainingPlans = ObserveTrainingPlans(trainingPlanRepository),
            trainingPlanRepository = trainingPlanRepository,
            getWorkoutDayContext = dayContext,
            progressionRecommendationRepository = recommendations,
            startWorkoutSession = StartWorkoutSession(workoutRepository, clock, ids),
            startWorkoutSessionFromPlan = StartWorkoutSessionFromPlan(workoutRepository, GetExercise(exerciseRepository), clock, ids),
            addWorkoutExercise = AddWorkoutExercise(workoutRepository, ids),
            recordWorkoutSet = RecordWorkoutSet(workoutRepository, clock, ids),
            undoLastWorkoutSet = UndoLastWorkoutSet(workoutRepository),
            editWorkoutSet = EditWorkoutSet(workoutRepository, clock),
            startRestTimer = StartRestTimer(workoutRepository, clock),
            adjustRestTimer = AdjustRestTimer(workoutRepository, clock),
            skipRestTimer = SkipRestTimer(workoutRepository),
            completeWorkoutSession =
                CompleteWorkoutSession(
                    workoutRepository,
                    trainingPlanRepository,
                    ComputeProgressionRecommendation(recommendations, dayContext, clock, SequentialIdentifierGenerator(prefix = "rec")),
                    clock,
                ),
            abandonWorkoutSession = AbandonWorkoutSession(workoutRepository, clock),
            settingsRepository = InMemorySettingsRepository(),
            restNotificationCanceller = RecordingRestNotificationCanceller(),
            workoutRepository = workoutRepository,
        )

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a set then a set both stay in press order`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                viewModel.onRecordSet(withExercise.id, 60.0, 9)
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                assertEquals(
                    listOf(8, 9),
                    stored(sessionId)
                        .exercises
                        .single()
                        .sets
                        .map { it.reps },
                )
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a set then Finish keeps the set`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                viewModel.onCompleteWorkout(sessionId)
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                val session = stored(sessionId)
                assertEquals(WorkoutSessionStatus.COMPLETED, session.status)
                assertEquals(
                    1,
                    session.exercises
                        .single()
                        .sets.size,
                )
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a set then Abandon keeps the set`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                viewModel.onAbandonWorkout(sessionId)
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                val session = stored(sessionId)
                assertEquals(WorkoutSessionStatus.ABANDONED, session.status)
                assertEquals(
                    1,
                    session.exercises
                        .single()
                        .sets.size,
                )
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a set then a correction of an earlier set changes the set the correction names`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                advanceUntilIdle()
                val first =
                    stored(sessionId)
                        .exercises
                        .single()
                        .sets
                        .single()
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 9)
                viewModel.onEditSet(withExercise.id, first.id, 70.0, 5)
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                val sets =
                    stored(sessionId)
                        .exercises
                        .single()
                        .sets
                assertEquals(listOf(70.0 to 5, 60.0 to 9), sets.map { it.load to it.reps })
                assertEquals(first.id, sets.first().id)
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a correction waiting behind the undo of its set changes nothing and reports the failure`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                viewModel.onRecordSet(withExercise.id, 60.0, 9)
                advanceUntilIdle()
                val (first, second) =
                    stored(sessionId)
                        .exercises
                        .single()
                        .sets
                val hold = holdWrites()

                viewModel.onUndoLastSet(withExercise.id)
                viewModel.onEditSet(withExercise.id, second.id, 70.0, 5)
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                assertEquals(
                    listOf(first),
                    stored(sessionId)
                        .exercises
                        .single()
                        .sets,
                )
                assertEquals(ActiveWorkoutErrorReason.NOT_FOUND, viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Only the first write is held, by clearing the hold once it waits: waiters
     * resume first in, first out, so with both held the failure would land first
     * and the test would pass without the lock.
     */
    @Test
    fun `an earlier failure cannot overwrite a later success`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                advanceUntilIdle()
                workoutRepository.holdWrites = null
                viewModel.onRecordSet(withExercise.id, 60.0, 9)
                advanceUntilIdle()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                hold.complete(Unit)
                advanceUntilIdle()

                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** Review I1: the screen can't see this rest coming, so it's the queue that has to end it. */
    @Test
    fun `a clock started while a set is saving ends the rest that save starts`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                var saves = 0
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8, onSaved = { saves++ })
                viewModel.onClockStarted()
                advanceUntilIdle()
                hold.complete(Unit)
                advanceUntilIdle()

                val session = stored(sessionId)
                assertNull(session.restTimer)
                assertEquals(
                    listOf(8),
                    session.exercises
                        .single()
                        .sets
                        .map { it.reps },
                )
                assertEquals(1, saves)
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The rest goes in behind the state's back, and nothing runs until `advanceUntilIdle`, so the state hasn't seen it at the tap. */
    @Test
    fun `a clock started before a stored rest reaches the screen ends that rest`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                advanceUntilIdle()

                requireSuccess(StartRestTimer(workoutRepository, clock)(sessionId, REST_SECONDS))
                assertNull((viewModel.uiState.value.content as ActiveWorkoutContent.Active).restTimer)
                viewModel.onClockStarted()
                advanceUntilIdle()

                assertNull(stored(sessionId).restTimer)
                assertNull((viewModel.uiState.value.content as ActiveWorkoutContent.Active).restTimer)
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a clock started with no rest anywhere writes nothing`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                advanceUntilIdle()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable

                viewModel.onClockStarted()
                advanceUntilIdle()

                assertEquals(
                    "A write would have used this failure up",
                    WorkoutPersistenceError.Unavailable,
                    workoutRepository.nextUpdateFailure,
                )
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Ending the earlier rest succeeds right behind the failed save. Had that cleared the
     * message, the person, already counting a new hold, would never hear the set wasn't saved.
     */
    @Test
    fun `a clock started behind a failed save leaves its message up and still ends the rest`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                requireSuccess(StartRestTimer(workoutRepository, clock)(sessionId, REST_SECONDS))
                advanceUntilIdle()
                val hold = holdWrites()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                advanceUntilIdle()
                workoutRepository.holdWrites = null
                viewModel.onClockStarted()
                advanceUntilIdle()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                hold.complete(Unit)
                advanceUntilIdle()

                val session = stored(sessionId)
                assertNull(session.restTimer)
                assertEquals(
                    emptyList<Int?>(),
                    session.exercises
                        .single()
                        .sets
                        .map { it.reps },
                )
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The plan's rule: a failed skip shows the one message, and the rest keeps running. */
    @Test
    fun `a clock started when the rest cannot be ended reports it and the rest stays`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                requireSuccess(StartRestTimer(workoutRepository, clock)(sessionId, REST_SECONDS))
                advanceUntilIdle()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable

                viewModel.onClockStarted()
                advanceUntilIdle()

                assertEquals(now.plusSeconds(REST_SECONDS.toLong()), stored(sessionId).restTimer?.endAt)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun holdWrites(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { workoutRepository.holdWrites = it }

    private suspend fun stored(id: WorkoutSessionId): WorkoutSession = requireNotNull(workoutRepository.findById(id))

    /** Scans forward rather than assuming an emission count per step (see `ActiveWorkoutViewModelTest`). */
    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.startWorkoutWithTheExercise(): ActiveExerciseUi {
        var state = awaitItem()
        while (state.content !is ActiveWorkoutContent.NoActiveSession) state = awaitItem()

        viewModel.onStartWorkout()
        state = awaitItem()
        while (state.content !is ActiveWorkoutContent.Active) state = awaitItem()

        viewModel.onAddExercise(state.availableExercises.single())
        var exercise: ActiveExerciseUi? = null
        while (exercise == null) {
            exercise = (awaitItem().content as? ActiveWorkoutContent.Active)?.exercises?.firstOrNull()
        }
        return exercise
    }

    private fun seedExercise() {
        exerciseRepository.seed(
            requireSuccess(
                Exercise.create(
                    id = ExerciseId("exercise-1"),
                    name = requireSuccess(ExerciseName.create("Back Squat")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            ),
        )
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private companion object {
        const val REST_SECONDS = 90
    }
}
