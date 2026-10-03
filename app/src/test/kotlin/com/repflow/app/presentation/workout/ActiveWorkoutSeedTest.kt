package com.repflow.app.presentation.workout

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
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
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.LastPerformance
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * B3 (remediation-1-remediation-1 CP9): the ViewModel computes a set entry's
 * seed - this session's last logged working set, else the last performance
 * (Q8) - and carries it, with the `Last time:` source, on [ActiveExerciseUi].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutSeedTest {
    private val clock = FixedClock(Instant.parse("2026-06-01T00:00:00Z"))
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val dayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock)
    private val fixtures = ProgressFixtures()
    private val benchId = ExerciseId("exercise-1")

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
            editLastWorkoutSet = EditLastWorkoutSet(workoutRepository, clock),
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

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun seedBench() {
        exerciseRepository.seed(
            requireSuccess(
                Exercise.create(
                    id = benchId,
                    name = requireSuccess(ExerciseName.create("Bench Press")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = clock.now(),
                ),
            ),
        )
    }

    private suspend fun seedHistory() {
        requireSuccess(
            workoutRepository.insert(
                fixtures.session(
                    Instant.parse("2026-05-05T08:00:00Z"),
                    fixtures.entry(benchId, fixtures.loaded(40.0, 10, warmup = true), fixtures.loaded(80.0, 8)),
                ),
            ),
        )
    }

    /** Starts an ad-hoc workout, adds bench, and returns its first state. */
    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.startWorkoutWithBench(): ActiveExerciseUi {
        var state = awaitItem()
        while (state.content !is ActiveWorkoutContent.NoActiveSession) state = awaitItem()
        viewModel.onStartWorkout()
        state = awaitItem()
        while (state.content as? ActiveWorkoutContent.Active == null || state.availableExercises.isEmpty()) state = awaitItem()
        viewModel.onAddExercise(state.availableExercises.single())
        return awaitExercise { true }
    }

    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.awaitExercise(accept: (ActiveExerciseUi) -> Boolean): ActiveExerciseUi {
        while (true) {
            val exercise = ((awaitItem().content as? ActiveWorkoutContent.Active)?.exercises)?.singleOrNull()
            if (exercise != null && accept(exercise)) return exercise
        }
    }

    private lateinit var viewModel: ActiveWorkoutViewModel

    @Test
    fun `an exercise done before seeds from its last working set and carries it for Last time`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedBench()
            seedHistory()
            viewModel = newViewModel()
            viewModel.uiState.test {
                val exercise = startWorkoutWithBench()

                assertEquals(SetEntrySeed(load = BigDecimal.valueOf(80.0), reps = BigDecimal(8)), exercise.seed)
                assertEquals(80.0, exercise.lastPerformance?.load)
                assertEquals(8, exercise.lastPerformance?.reps)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a never-done exercise has no seed and no last performance`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedBench()
            viewModel = newViewModel()
            viewModel.uiState.test {
                val exercise = startWorkoutWithBench()

                assertNull(exercise.seed)
                assertNull(exercise.lastPerformance)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `this session's last working set replaces the history seed once logged, Last time stays history`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedBench()
            seedHistory()
            viewModel = newViewModel()
            viewModel.uiState.test {
                val exercise = startWorkoutWithBench()

                viewModel.onRecordSet(exercise.id, 85.0, 5)
                val withSet = awaitExercise { it.sets.isNotEmpty() }

                assertEquals(SetEntrySeed(load = BigDecimal.valueOf(85.0), reps = BigDecimal(5)), withSet.seed)
                assertEquals(80.0, withSet.lastPerformance?.load)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a warm-up-only exercise seeds from its last set, not from history`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedBench()
            seedHistory()
            viewModel = newViewModel()
            viewModel.uiState.test {
                val exercise = startWorkoutWithBench()

                viewModel.onRecordSet(exercise.id, 40.0, 10, isWarmup = true)
                val withSet = awaitExercise { it.sets.isNotEmpty() }

                assertEquals(SetEntrySeed(load = BigDecimal.valueOf(40.0), reps = BigDecimal(10)), withSet.seed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `finishing a workout re-reads history so the next workout seeds from it`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedBench()
            viewModel = newViewModel()
            viewModel.uiState.test {
                val exercise = startWorkoutWithBench()
                viewModel.onRecordSet(exercise.id, 90.0, 3)
                awaitExercise { it.sets.isNotEmpty() }
                viewModel.onCompleteWorkout(requireNotNull(workoutRepository.findActiveSession()).id)
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.NoActiveSession) state = awaitItem()

                viewModel.onStartWorkout()
                state = awaitItem()
                while (state.content as? ActiveWorkoutContent.Active == null) state = awaitItem()
                viewModel.onAddExercise(state.availableExercises.single())
                val next = awaitExercise { true }

                assertEquals(90.0, next.lastPerformance?.load)
                assertEquals(SetEntrySeed(load = BigDecimal.valueOf(90.0), reps = BigDecimal(3)), next.seed)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the seed carries only the fields the tracking type records`() {
        val sets = listOf(ActiveSetUi(WorkoutSetId("s"), 1, load = 20.0, reps = 9, durationSeconds = null))

        assertEquals(
            SetEntrySeed(reps = BigDecimal(9)),
            entrySeedOf(ExerciseTrackingType.REPS_ONLY, sets, null),
        )
        val held = listOf(ActiveSetUi(WorkoutSetId("s"), 1, load = null, reps = null, durationSeconds = 45))
        assertEquals(SetEntrySeed(seconds = BigDecimal(45)), entrySeedOf(ExerciseTrackingType.DURATION, held, null))
        // A history entry recorded with a load seeds a reps-only exercise with reps only.
        val last = LastPerformance(load = 20.0, reps = 9, durationSeconds = null, date = Instant.EPOCH)
        assertEquals(SetEntrySeed(reps = BigDecimal(9)), entrySeedOf(ExerciseTrackingType.REPS_ONLY, emptyList(), last))
        assertNull(entrySeedOf(ExerciseTrackingType.WEIGHT_AND_REPS, emptyList(), null))
    }
}
