package com.repflow.app.presentation.workout

import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.progression.RecordManualOverride
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
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
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
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
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val progressionRecommendationRepository = InMemoryProgressionRecommendationRepository()
    private val viewModel =
        ActiveWorkoutViewModel(
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workoutRepository),
            observeExercises = ObserveExercises(exerciseRepository),
            observeTrainingPlans = ObserveTrainingPlans(trainingPlanRepository),
            getExercise = GetExercise(exerciseRepository),
            getWorkoutDayContext = GetWorkoutDayContext(InMemoryRecoveryRepository(), InMemoryFutsalRepository(), clock),
            progressionRecommendationRepository = progressionRecommendationRepository,
            recordManualOverride = RecordManualOverride(progressionRecommendationRepository, clock),
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
                    trainingPlanRepository,
                    ComputeProgressionRecommendation(
                        progressionRecommendationRepository,
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
    fun `onStartWorkout with a plan version seeds every planned exercise with its plannedExerciseId`() =
        runTest {
            val exercise = seedExercise()
            val plannedExercise =
                PlannedExercise(
                    id = PlannedExerciseId("planned-1"),
                    exerciseId = exercise.id,
                    order = 0,
                    targetSets = requireSuccess(TargetSets.create(3)),
                    target = PlannedExerciseTarget.Reps(requireSuccess(RepRange.create(8, 12))),
                    restDuration = null,
                    isOptional = false,
                )
            val plan =
                requireSuccess(
                    TrainingPlan.create(
                        id = TrainingPlanId("plan-1"),
                        name = requireSuccess(TrainingPlanName.create("Push day")),
                        createdAt = now,
                    ),
                )
            val version =
                requireSuccess(
                    TrainingPlanVersion.create(
                        id = TrainingPlanVersionId("version-1"),
                        planId = plan.id,
                        versionNumber = 1,
                        plannedExercises = listOf(plannedExercise),
                        note = null,
                        createdAt = now,
                    ),
                )
            requireSuccess(trainingPlanRepository.createPlanWithFirstVersion(plan, version))

            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                // The training-plan overview list is populated by its own `init`-launched
                // collector (mirrors dayContext's one-shot fetch), a separate coroutine from
                // uiState's own combine - under UnconfinedTestDispatcher the two can settle
                // in a different number of intermediate emissions than a naive "Loading, then
                // NoActiveSession" count would assume, so scan forward to the first state that
                // actually has both instead of asserting on a fixed awaitItem() count.
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.NoActiveSession || state.availablePlans.isEmpty()) {
                    state = awaitItem()
                }
                val pickedPlan = state.availablePlans.single()
                assertEquals(version.id, pickedPlan.versionId)
                assertEquals("Push day", pickedPlan.planName)

                viewModel.onStartWorkout(pickedPlan.versionId)

                var afterStart = awaitItem()
                while ((afterStart.content as? ActiveWorkoutContent.Active)?.exercises?.isEmpty() != false) {
                    afterStart = awaitItem()
                }
                val active = (afterStart.content as ActiveWorkoutContent.Active).exercises.single()
                assertEquals(exercise.name.value, active.name)
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
