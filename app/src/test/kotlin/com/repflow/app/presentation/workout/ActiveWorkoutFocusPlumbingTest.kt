package com.repflow.app.presentation.workout

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
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseInstructions
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.LoadIncrement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * Focus mode's plumbing through [ActiveWorkoutViewModel] (remediation-1 CP8):
 * each workout exercise carries the library exercise it records, that
 * exercise's load increment converted from grams to the stepper's kilograms,
 * and its technique notes - read from archived exercises too, since one can be
 * archived after it was added to the workout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutFocusPlumbingTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val recommendations = InMemoryProgressionRecommendationRepository()
    private val dayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock)
    private val viewModel by lazy {
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
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun exercise(
        id: String,
        name: String,
        incrementGrams: Long?,
        instructions: String?,
    ): Exercise =
        success(
            Exercise.create(
                id = ExerciseId(id),
                name = success(ExerciseName.create(name)),
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = success(ExerciseInstructions.createOrNull(instructions)),
                defaultLoadIncrement = incrementGrams?.let { success(LoadIncrement.create(it)) },
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = now,
            ),
        )

    @Test
    fun aWorkoutExerciseCarriesItsExerciseIdLoadIncrementInKgAndTechniqueNotes() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val bench = exercise("exercise-bench", "Bench Press", incrementGrams = 1_250, instructions = "Shoulder blades back.")
            val curl = exercise("exercise-curl", "Curl", incrementGrams = null, instructions = null).archive(now)
            exerciseRepository.seed(bench)
            exerciseRepository.seed(curl)
            val sessionId = success(StartWorkoutSession(workoutRepository, clock, ids)(StartWorkoutSessionCommand(null)))
            listOf(bench, curl).forEach { added ->
                success(
                    AddWorkoutExercise(workoutRepository, ids)(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = added.id,
                            exerciseNameSnapshot = added.name.value,
                            trackingType = added.trackingType,
                            plannedExerciseId = null,
                        ),
                    ),
                )
            }

            viewModel.uiState.test {
                var state = awaitItem()
                while ((state.content as? ActiveWorkoutContent.Active)?.exercises?.size != 2) state = awaitItem()
                val (benchUi, curlUi) = (state.content as ActiveWorkoutContent.Active).exercises

                assertEquals(bench.id, benchUi.exerciseId)
                assertEquals(0, BigDecimal("1.25").compareTo(benchUi.defaultLoadIncrement))
                assertEquals("Shoulder blades back.", benchUi.instructions)
                assertEquals(curl.id, curlUi.exerciseId)
                assertNull(curlUi.defaultLoadIncrement)
                assertNull(curlUi.instructions)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anArchivedExercisesIncrementAndNotesStillReachTheWorkout() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val press = exercise("exercise-press", "Overhead Press", incrementGrams = 500, instructions = "Brace.").archive(now)
            exerciseRepository.seed(press)
            val sessionId = success(StartWorkoutSession(workoutRepository, clock, ids)(StartWorkoutSessionCommand(null)))
            success(
                AddWorkoutExercise(workoutRepository, ids)(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = press.id,
                        exerciseNameSnapshot = press.name.value,
                        trackingType = press.trackingType,
                        plannedExerciseId = null,
                    ),
                ),
            )

            viewModel.uiState.test {
                var state = awaitItem()
                while ((state.content as? ActiveWorkoutContent.Active)?.exercises?.isEmpty() != false) state = awaitItem()
                val pressUi = (state.content as ActiveWorkoutContent.Active).exercises.single()

                assertEquals(0, BigDecimal("0.5").compareTo(pressUi.defaultLoadIncrement))
                assertEquals("Brace.", pressUi.instructions)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
