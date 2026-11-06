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
 * A-1 (fix-workout-errors): what a failed workout action reports to the screen,
 * and that `onSaved`, which is what clears the screen's entry, runs only after a
 * save has succeeded. The real use cases run over [InMemoryWorkoutRepository],
 * whose `nextUpdateFailure` forces the failed save and `readsBeforeFailure` the
 * failed read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutActionErrorTest {
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

    /** A failed save reports the error and leaves the entry alone: `onSaved` is what clears it. */
    @Test
    fun `a failed Log set reports the error and does not invoke onSaved`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var saved = 0
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable

                viewModel.onRecordSet(withExercise.id, 60.0, 8, onSaved = { saved++ })

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                advanceUntilIdle()
                assertEquals(0, saved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a successful Log set invokes onSaved exactly once and reports no error`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var saved = 0
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()

                viewModel.onRecordSet(withExercise.id, 60.0, 8, onSaved = { saved++ })
                advanceUntilIdle()

                assertEquals(
                    1,
                    requireNotNull(workoutRepository.findActiveSession())
                        .exercises
                        .single()
                        .sets.size,
                )
                assertEquals(1, saved)
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onErrorShown clears the reported error`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())

                viewModel.onErrorShown()
                advanceUntilIdle()

                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed save on finishing reports the error and keeps the session`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable

                viewModel.onCompleteWorkout(sessionId)

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                assertEquals(WorkoutFinishState.Idle, viewModel.finish.value)
                assertEquals(sessionId, workoutRepository.findActiveSession()?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed save on abandoning reports the error and keeps the session`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable

                viewModel.onAbandonWorkout(sessionId)

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                assertEquals(sessionId, workoutRepository.findActiveSession()?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** A failed read of the workout is a failed action, not a closed app. */
    @Test
    fun `a failed read on Log set reports the error and does not invoke onSaved`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var saved = 0
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                workoutRepository.readsBeforeFailure = 0

                viewModel.onRecordSet(withExercise.id, 60.0, 8, onSaved = { saved++ })

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                advanceUntilIdle()
                assertEquals(0, saved)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed read on finishing reports the error and returns the confirm to idle`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.readsBeforeFailure = 0

                viewModel.onCompleteWorkout(sessionId)

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                assertEquals(WorkoutFinishState.Idle, viewModel.finish.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a failed read on abandoning reports the error`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.readsBeforeFailure = 0

                viewModel.onAbandonWorkout(sessionId)

                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * The set is saved by the time the rest timer reads the session, so that
     * read failing must not report the set as failed: the entry would stay
     * filled and a second press would log it twice.
     */
    @Test
    fun `a failed rest timer read does not report a saved set as failed`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            var saved = 0
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                // RecordWorkoutSet's own read succeeds; StartRestTimer's is the one that throws.
                workoutRepository.readsBeforeFailure = 1

                viewModel.onRecordSet(withExercise.id, 60.0, 8, onSaved = { saved++ })
                advanceUntilIdle()

                assertEquals(1, saved)
                assertNull(viewModel.uiState.value.errorMessage)
                workoutRepository.readsBeforeFailure = null
                val session = requireNotNull(workoutRepository.findActiveSession())
                assertEquals(
                    1,
                    session.exercises
                        .single()
                        .sets.size,
                )
                assertNull(session.restTimer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Someone who reads the message and presses again must not still be looking
     * at it once the press worked, or they log the set twice. Nothing here calls
     * `onErrorShown`: the success is what clears the field.
     */
    @Test
    fun `a successful action clears the error and a new failure shows again`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithTheExercise()
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                advanceUntilIdle()

                // Also drains the queue, so the scan below can't stop on a state from before the clear.
                assertNull(expectMostRecentItem().errorMessage)

                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a successful finish clears the error`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                viewModel.onCompleteWorkout(sessionId)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())

                viewModel.onCompleteWorkout(sessionId)
                advanceUntilIdle()

                assertEquals(WorkoutFinishState.Finished(sessionId), viewModel.finish.value)
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a successful abandon clears the error`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                startWorkoutWithTheExercise()
                val sessionId = requireNotNull(workoutRepository.findActiveSession()).id
                workoutRepository.nextUpdateFailure = WorkoutPersistenceError.Unavailable
                viewModel.onAbandonWorkout(sessionId)
                assertEquals(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE, awaitActionError())

                viewModel.onAbandonWorkout(sessionId)
                advanceUntilIdle()

                assertNull(workoutRepository.findActiveSession())
                assertNull(viewModel.uiState.value.errorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

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

    /** Scans forward to the first state reporting an action error. */
    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.awaitActionError(): ActiveWorkoutErrorReason {
        var state = awaitItem()
        while (state.errorMessage == null) state = awaitItem()
        return checkNotNull(state.errorMessage)
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
}
