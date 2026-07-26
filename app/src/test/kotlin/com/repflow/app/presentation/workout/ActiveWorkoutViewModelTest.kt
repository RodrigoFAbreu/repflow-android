package com.repflow.app.presentation.workout

import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
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
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActiveWorkoutViewModelTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "session")
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val viewModel =
        ActiveWorkoutViewModel(
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workoutRepository),
            observeExercises = ObserveExercises(exerciseRepository),
            getWorkoutDayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock),
            startWorkoutSession = StartWorkoutSession(workoutRepository, clock, ids),
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
                    InMemoryTrainingPlanRepository(),
                    ComputeProgressionRecommendation(
                        InMemoryProgressionRecommendationRepository(),
                        GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock),
                        clock,
                        SequentialIdentifierGenerator(prefix = "rec"),
                    ),
                    clock,
                ),
            abandonWorkoutSession = AbandonWorkoutSession(workoutRepository, clock),
        )

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun seedExercise(): Exercise {
        val exercise =
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
            )
        exerciseRepository.seed(exercise)
        return exercise
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading then shows no active session when none exists`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                assertEquals(ActiveWorkoutContent.Loading, awaitItem().content)
                assertEquals(ActiveWorkoutContent.NoActiveSession, awaitItem().content)
            }
        }

    @Test
    fun `onStartWorkout transitions to an active session with zero exercises`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // NoActiveSession

                viewModel.onStartWorkout()

                val active = awaitItem().content as ActiveWorkoutContent.Active
                assertEquals(emptyList<ActiveExerciseUi>(), active.exercises)
            }
        }

    @Test
    fun `adding an exercise then recording, editing and undoing a set updates the active session`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // NoActiveSession

                viewModel.onStartWorkout()
                val activeEmpty = awaitItem() // Active, no exercises

                val exercisePicked = activeEmpty.availableExercises.single()
                viewModel.onAddExercise(exercisePicked)
                val withExercise = (awaitItem().content as ActiveWorkoutContent.Active).exercises.single()

                viewModel.onRecordSet(withExercise.id, 60.0, 8)
                val withSet =
                    (awaitItem().content as ActiveWorkoutContent.Active)
                        .exercises
                        .single()
                        .sets
                        .single()
                assertEquals(60.0, withSet.load)
                assertEquals(8, withSet.reps)

                viewModel.onEditLastSet(withExercise.id, 70.0, 5)
                val edited =
                    (awaitItem().content as ActiveWorkoutContent.Active)
                        .exercises
                        .single()
                        .sets
                        .single()
                assertEquals(70.0, edited.load)
                assertEquals(5, edited.reps)

                viewModel.onUndoLastSet(withExercise.id)
                val undone = (awaitItem().content as ActiveWorkoutContent.Active).exercises.single().sets
                assertEquals(emptyList<ActiveSetUi>(), undone)
            }
        }

    @Test
    fun `onCompleteWorkout returns to no active session`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // NoActiveSession

                viewModel.onStartWorkout()
                val active = awaitItem().content as ActiveWorkoutContent.Active

                viewModel.onCompleteWorkout(active.sessionId)

                assertEquals(ActiveWorkoutContent.NoActiveSession, awaitItem().content)
            }
        }
}
