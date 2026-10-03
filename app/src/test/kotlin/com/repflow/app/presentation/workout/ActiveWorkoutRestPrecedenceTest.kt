package com.repflow.app.presentation.workout

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
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.InMemoryWorkoutRepository
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
import com.repflow.app.domain.exercise.RestDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Q9 (remediation-1-remediation-1 CP6): the rest after a set is the plan row's,
 * else the exercise's own `Default rest`, else the app-wide default - on the
 * path that has no plan row at all (an ad hoc or mid-workout exercise).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutRestPrecedenceTest {
    private val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val dayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock)
    private val canceller = RecordingRestNotificationCanceller()
    private val settingsRepository = InMemorySettingsRepository()
    private val now = Instant.parse("2026-01-01T00:00:00Z")

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
            settingsRepository = settingsRepository,
            restNotificationCanceller = canceller,
            workoutRepository = workoutRepository,
        )

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun seed(
        id: String,
        restSeconds: Long?,
    ) {
        val rest = restSeconds?.let { (RestDuration.create(it) as DomainResult.Success).value }
        val created =
            Exercise.create(
                id = ExerciseId(id),
                name = (ExerciseName.create(id) as DomainResult.Success).value,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = rest,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = now,
            )
        exerciseRepository.seed((created as DomainResult.Success).value)
    }

    /** Starts an empty workout, adds [exerciseId] ad hoc (no plan row), logs one set and returns the rest it started. */
    private suspend fun TestScope.restAfterAdHocSet(
        viewModel: ActiveWorkoutViewModel,
        exerciseId: String,
    ): Int? {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.onStartWorkout()
        viewModel.onAddExercise(
            viewModel.uiState.value.availableExercises
                .single { it.id == ExerciseId(exerciseId) },
        )
        val workoutExercise = (viewModel.uiState.value.content as ActiveWorkoutContent.Active).exercises.single()
        viewModel.onRecordSet(workoutExercise.id, 60.0, 8)
        return workoutRepository.findActiveSession()?.restTimer?.totalDurationSeconds
    }

    @Test
    fun `an ad hoc exercise with its own default rest starts that rest, not the app default`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seed("squat", restSeconds = 150)
            val viewModel = newViewModel()

            assertEquals(150, restAfterAdHocSet(viewModel, "squat"))
        }

    @Test
    fun `an ad hoc exercise without a default rest starts the app default`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seed("squat", restSeconds = null)
            settingsRepository.update { it.copy(defaultRestSeconds = 75) }
            val viewModel = newViewModel()

            assertEquals(75, restAfterAdHocSet(viewModel, "squat"))
        }

    @Test
    fun `a changed app default applies to the next set`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seed("squat", restSeconds = null)
            val viewModel = newViewModel()
            assertEquals(AppSettings.DEFAULT_REST_SECONDS, restAfterAdHocSet(viewModel, "squat"))

            settingsRepository.update { it.copy(defaultRestSeconds = 200) }
            val workoutExercise = (viewModel.uiState.value.content as ActiveWorkoutContent.Active).exercises.single()
            viewModel.onRecordSet(workoutExercise.id, 60.0, 8)

            assertEquals(200, workoutRepository.findActiveSession()?.restTimer?.totalDurationSeconds)
        }

    @Test
    fun `the ui state carries the exercise default rest, the app default and the extra set fields mode`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seed("squat", restSeconds = 150)
            settingsRepository.update { it.copy(defaultRestSeconds = 75, extraSetFields = ExtraSetFields.OFF) }
            val viewModel = newViewModel()
            restAfterAdHocSet(viewModel, "squat")

            val active = viewModel.uiState.value.content as ActiveWorkoutContent.Active
            assertEquals(150, active.exercises.single().defaultRestSeconds)
            assertEquals(75, active.appDefaultRestSeconds)
            assertEquals(ExtraSetFields.OFF, active.extraSetFields)
        }
}
