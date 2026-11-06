package com.repflow.app.presentation.workout

import app.cash.turbine.test
import com.repflow.app.application.exercise.ExerciseQueryCriteria
import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progress.ProgressFixtures
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
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditWorkoutSet
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * A-12 (fix-workout-errors): `Try again` on the workout's load-failure state. The
 * real use cases run over [InMemoryWorkoutRepository], which has no
 * observation-failure hook, so [FailsFirstObservationRepository] fails the first
 * subscription, as a Room `Flow` that throws would. It and
 * [BreakableExerciseRepository] also fail the history read and the exercise read
 * until a test heals them, because `Try again` re-reads those too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutObservationRetryTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val workoutRepository = FailsFirstObservationRepository(InMemoryWorkoutRepository())
    private val exerciseRepository = BreakableExerciseRepository(InMemoryExerciseRepository())
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val dayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock)
    private val exerciseId = ExerciseId("exercise-1")
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

    /** The failed observation has completed, so only a new subscription leaves `ObservationFailed`. */
    @Test
    fun `onRetryObservation leaves a failed observation for the active session`() =
        runTest {
            startSession()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.ObservationFailed) state = awaitItem()

                viewModel.onRetryObservation()

                while (state.content !is ActiveWorkoutContent.Active) state = awaitItem()
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** `Loading` can conflate away and an equal `Active` doesn't re-emit, so the proof is a later change still arriving. */
    @Test
    fun `a second onRetryObservation while active does not break the stream`() =
        runTest {
            seedExercise()
            startSession()
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.ObservationFailed) state = awaitItem()
                viewModel.onRetryObservation()
                while (state.content !is ActiveWorkoutContent.Active) state = awaitItem()

                viewModel.onRetryObservation()
                advanceUntilIdle()
                val available = viewModel.uiState.value.availableExercises
                viewModel.onAddExercise(available.single())

                var exercises = emptyList<ActiveExerciseUi>()
                while (exercises.isEmpty()) exercises = (awaitItem().content as? ActiveWorkoutContent.Active)?.exercises.orEmpty()
                assertEquals("Back Squat", exercises.single().name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The picker's failed read has completed, and nothing but `Try again` reads it again. */
    @Test
    fun `onRetryObservation re-reads the exercise picker each time`() =
        runTest {
            seedExercise()
            startSession()
            exerciseRepository.failing = true
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.ObservationFailed) state = awaitItem()
                advanceUntilIdle()
                assertEquals(emptyList<ExercisePickerItem>(), viewModel.uiState.value.availableExercises)

                viewModel.onRetryObservation()
                advanceUntilIdle()
                assertEquals(emptyList<ExercisePickerItem>(), viewModel.uiState.value.availableExercises)

                exerciseRepository.failing = false
                viewModel.onRetryObservation()
                advanceUntilIdle()

                assertEquals(
                    listOf("Back Squat"),
                    viewModel.uiState.value.availableExercises
                        .map { it.name },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    /** The seeds are a one-shot read, so a failed one stays empty until something reads it again. */
    @Test
    fun `onRetryObservation re-reads the last-session seeds each time`() =
        runTest {
            seedExercise()
            seedHistory()
            startSessionWithTheExercise()
            workoutRepository.failHistoryReads = true
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.ObservationFailed) state = awaitItem()

                viewModel.onRetryObservation()
                advanceUntilIdle()
                assertNull(activeExercise().lastPerformance)
                assertNull(activeExercise().seed)

                workoutRepository.failHistoryReads = false
                viewModel.onRetryObservation()
                advanceUntilIdle()

                assertEquals(80.0, activeExercise().lastPerformance?.load)
                assertEquals(SetEntrySeed(load = BigDecimal.valueOf(80.0), reps = BigDecimal(8)), activeExercise().seed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun activeExercise(): ActiveExerciseUi = (viewModel.uiState.value.content as ActiveWorkoutContent.Active).exercises.single()

    private suspend fun startSession(): WorkoutSessionId =
        requireSuccess(StartWorkoutSession(workoutRepository, clock, ids)(StartWorkoutSessionCommand(trainingPlanVersionId = null)))

    private suspend fun startSessionWithTheExercise() {
        requireSuccess(
            AddWorkoutExercise(workoutRepository, ids)(
                AddWorkoutExerciseCommand(
                    sessionId = startSession(),
                    exerciseId = exerciseId,
                    exerciseNameSnapshot = "Back Squat",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                ),
            ),
        )
    }

    private suspend fun seedHistory() {
        val fixtures = ProgressFixtures()
        requireSuccess(
            workoutRepository.insert(
                fixtures.session(Instant.parse("2025-12-01T08:00:00Z"), fixtures.entry(exerciseId, fixtures.loaded(80.0, 8))),
            ),
        )
    }

    private fun seedExercise() {
        exerciseRepository.delegate.seed(
            requireSuccess(
                Exercise.create(
                    id = exerciseId,
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

    private class FailsFirstObservationRepository(
        private val delegate: WorkoutRepository,
    ) : WorkoutRepository by delegate {
        private var failed = false

        var failHistoryReads = false

        override fun observeActiveSession(): Flow<WorkoutSession?> =
            flow {
                if (!failed) {
                    failed = true
                    error("Simulated observation failure")
                }
                emitAll(delegate.observeActiveSession())
            }

        override fun observeCompletedSessions(includeInvalidated: Boolean): Flow<List<WorkoutSession>> =
            flow {
                if (failHistoryReads) error("Simulated history read failure")
                emitAll(delegate.observeCompletedSessions(includeInvalidated))
            }
    }

    /**
     * Fails every subscription until healed. [InMemoryExerciseRepository]'s own hook
     * fails only the next one, which the board's exercise details, not the picker,
     * can be the first to take.
     */
    private class BreakableExerciseRepository(
        val delegate: InMemoryExerciseRepository,
    ) : ExerciseRepository by delegate {
        var failing = false

        override fun observe(criteria: ExerciseQueryCriteria): Flow<List<Exercise>> =
            flow {
                if (failing) error("Simulated exercise read failure")
                emitAll(delegate.observe(criteria))
            }
    }
}
