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

    private fun seedDurationExercise(): Exercise {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = ExerciseId("exercise-plank"),
                    name = requireSuccess(ExerciseName.create("Plank")),
                    trackingType = ExerciseTrackingType.DURATION,
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
    fun `an archived plan is excluded from the start-workout plan picker`() =
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
                        name = requireSuccess(TrainingPlanName.create("Retired plan")),
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
            val archived = plan.archive(now)
            requireSuccess(trainingPlanRepository.updatePlan(archived))

            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content !is ActiveWorkoutContent.NoActiveSession) {
                    state = awaitItem()
                }
                assertEquals(emptyList<TrainingPlanPickerItem>(), state.availablePlans)
                expectNoEvents()
            }
        }

    @Test
    fun `recording a set updates the active session`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithFirstAvailableExercise()

                // durationSeconds is deliberately omitted here: WorkoutSet.validateTrackedValues
                // rejects a non-null durationSeconds for a WEIGHT_AND_REPS exercise
                // (DurationNotApplicable) - seedExercise() creates a WEIGHT_AND_REPS exercise, so
                // only RPE/isWarmup are exercised here; duration-tracked exercises are covered
                // separately below.
                viewModel.onRecordSet(withExercise.id, 60.0, 8, null, 7.5, true)
                val withSet = awaitSingleSet()
                assertEquals(60.0, withSet.load)
                assertEquals(8, withSet.reps)
                assertEquals(7.5, withSet.rpe)
                assertEquals(true, withSet.isWarmup)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `editing the last set updates load, reps, rpe and warm-up status`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithFirstAvailableExercise()
                viewModel.onRecordSet(withExercise.id, 60.0, 8, null, 7.5, true)
                awaitSingleSet()

                viewModel.onEditLastSet(withExercise.id, 70.0, 5, null, 8.0, false)
                var state = awaitItem()
                while ((state.content as ActiveWorkoutContent.Active)
                        .exercises
                        .single()
                        .sets
                        .single()
                        .load != 70.0
                ) {
                    state = awaitItem()
                }
                val edited =
                    (state.content as ActiveWorkoutContent.Active)
                        .exercises
                        .single()
                        .sets
                        .single()
                assertEquals(70.0, edited.load)
                assertEquals(5, edited.reps)
                assertEquals(8.0, edited.rpe)
                assertEquals(false, edited.isWarmup)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recording a set with pain and technique quality persists both fields`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithFirstAvailableExercise()

                viewModel.onRecordSet(withExercise.id, 60.0, 8, null, 7.5, true, 3, 4)
                val withSet = awaitSingleSet()
                assertEquals(3, withSet.pain)
                assertEquals(4, withSet.techniqueQuality)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `undoing the last set removes it`() =
        runTest {
            seedExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithFirstAvailableExercise()
                viewModel.onRecordSet(withExercise.id, 60.0, 8, null, 7.5, true)
                awaitSingleSet()

                viewModel.onUndoLastSet(withExercise.id)
                var state = awaitItem()
                while ((state.content as ActiveWorkoutContent.Active)
                        .exercises
                        .single()
                        .sets
                        .isNotEmpty()
                ) {
                    state = awaitItem()
                }
                val undone = (state.content as ActiveWorkoutContent.Active).exercises.single().sets
                assertEquals(emptyList<ActiveSetUi>(), undone)
            }
        }

    @Test
    fun `recording a set for a duration-tracked exercise persists durationSeconds`() =
        runTest {
            seedDurationExercise()
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                val withExercise = startWorkoutWithFirstAvailableExercise()

                // load/reps are deliberately null here: a DURATION exercise rejects them
                // (RepsNotApplicable/LoadNotApplicable), mirroring the WEIGHT_AND_REPS case's
                // rejection of durationSeconds above.
                viewModel.onRecordSet(withExercise.id, null, null, 60, 6.0, false)
                val withSet = awaitSingleSet()
                assertEquals(60, withSet.durationSeconds)
                assertEquals(6.0, withSet.rpe)
                assertEquals(false, withSet.isWarmup)
                // onRecordSet's success path also calls startRestTimer, which produces one more
                // combine emission after the one just consumed above - not relevant here.
                cancelAndIgnoreRemainingEvents()
            }
        }

    /**
     * Starts an ad-hoc workout and adds the (single) exercise the test seeded, returning it.
     * Scans forward with `awaitItem()` rather than assuming a fixed emission count per step
     * (Milestone 8, CP7 root-caused the pre-existing flakiness here: under
     * UnconfinedTestDispatcher, the number of intermediate `uiState` combine emissions per
     * state change isn't guaranteed).
     */
    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.startWorkoutWithFirstAvailableExercise(): ActiveExerciseUi {
        var state = awaitItem()
        while (state.content !is ActiveWorkoutContent.NoActiveSession) state = awaitItem()

        viewModel.onStartWorkout()
        state = awaitItem()
        while ((state.content as? ActiveWorkoutContent.Active) == null) state = awaitItem()

        val exercisePicked = state.availableExercises.single()
        viewModel.onAddExercise(exercisePicked)
        state = awaitItem()
        while ((state.content as? ActiveWorkoutContent.Active)?.exercises?.isEmpty() != false) {
            state = awaitItem()
        }
        return (state.content as ActiveWorkoutContent.Active).exercises.single()
    }

    /** Scans forward to the first state with a recorded set on the (single) active exercise. */
    private suspend fun ReceiveTurbine<ActiveWorkoutUiState>.awaitSingleSet(): ActiveSetUi {
        var state = awaitItem()
        while ((state.content as? ActiveWorkoutContent.Active)
                ?.exercises
                ?.single()
                ?.sets
                ?.isEmpty() != false
        ) {
            state = awaitItem()
        }
        return (state.content as ActiveWorkoutContent.Active)
            .exercises
            .single()
            .sets
            .single()
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
