package com.repflow.app.application.trainingplan

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreateTrainingPlanTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan-entity")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)

    private fun seedExercise(
        id: String = "bench-press",
        name: String = "Bench Press",
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    ): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            requireSuccessExercise(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccessName(ExerciseName.create(name)),
                    trackingType = trackingType,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            )
        exerciseRepository.seed(exercise)
        return exerciseId
    }

    private fun repsRow(
        exerciseId: String,
        order: Int = 0,
    ) = PlannedExerciseInput(
        exerciseId = exerciseId,
        order = order,
        targetSets = 3,
        targetKind = PlannedExerciseTargetKind.REPS,
        repMin = 8,
        repMax = 12,
        durationMinSeconds = null,
        durationMaxSeconds = null,
        restSeconds = 90,
        isOptional = false,
    )

    @Test
    fun `creates a plan with an ordered planned exercise`() =
        runTest {
            val exerciseId = seedExercise()

            val result = createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value))))

            val planId = requireSuccess(result)
            val overview = requireNotNull(planRepository.findOverviewByPlanId(planId))
            assertEquals("Push Day", overview.plan.name.value)
            assertEquals(1, overview.latestVersion.versionNumber)
            assertEquals(1, overview.latestVersion.plannedExercises.size)
            assertEquals(now, overview.plan.createdAt)
        }

    @Test
    fun `rejects a duplicate plan name detected by the precheck`() =
        runTest {
            val exerciseId = seedExercise()
            requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value)))))

            val result = createTrainingPlan(CreateTrainingPlanCommand("  push   day  ", listOf(repsRow(exerciseId.value))))

            assertEquals(TrainingPlanOperationError.DuplicateName, requireFailure(result))
        }

    @Test
    fun `rejects a blank name as a validation failure`() =
        runTest {
            val exerciseId = seedExercise()

            val result = createTrainingPlan(CreateTrainingPlanCommand("   ", listOf(repsRow(exerciseId.value))))

            val error = requireFailure(result)
            assertTrue(error is TrainingPlanOperationError.ValidationFailed)
            assertEquals(
                listOf(TrainingPlanValidationError.NameBlank),
                (error as TrainingPlanOperationError.ValidationFailed).errors,
            )
        }

    @Test
    fun `rejects an empty planned exercise list`() =
        runTest {
            val result = createTrainingPlan(CreateTrainingPlanCommand("Push Day", emptyList()))

            val error = requireFailure(result)
            assertTrue(error is TrainingPlanOperationError.ValidationFailed)
            assertEquals(
                listOf(TrainingPlanValidationError.NoPlannedExercises),
                (error as TrainingPlanOperationError.ValidationFailed).errors,
            )
        }

    @Test
    fun `rejects a planned exercise referencing an exercise that does not exist`() =
        runTest {
            val result = createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow("missing-exercise"))))

            val error = requireFailure(result)
            assertTrue(error is TrainingPlanOperationError.PlannedExerciseInvalid)
            assertEquals(
                listOf(PlannedExerciseValidationError.ExerciseNotFound(0)),
                (error as TrainingPlanOperationError.PlannedExerciseInvalid).errors,
            )
        }

    @Test
    fun `rejects a reps target on a duration-only exercise`() =
        runTest {
            val exerciseId = seedExercise(id = "plank", name = "Plank", trackingType = ExerciseTrackingType.DURATION)

            val result = createTrainingPlan(CreateTrainingPlanCommand("Core Day", listOf(repsRow(exerciseId.value))))

            val error = requireFailure(result)
            assertTrue(error is TrainingPlanOperationError.PlannedExerciseInvalid)
            assertEquals(
                listOf(PlannedExerciseValidationError.TargetKindMismatch(0)),
                (error as TrainingPlanOperationError.PlannedExerciseInvalid).errors,
            )
        }

    @Test
    fun `rejects a gap in the planned exercise order sequence`() =
        runTest {
            val exerciseId = seedExercise()

            val result = createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value, order = 1))))

            val error = requireFailure(result)
            assertTrue(error is TrainingPlanOperationError.ValidationFailed)
            assertEquals(
                listOf(TrainingPlanValidationError.InvalidOrderSequence),
                (error as TrainingPlanOperationError.ValidationFailed).errors,
            )
        }

    @Test
    fun `surfaces an unavailable persistence failure`() =
        runTest {
            val exerciseId = seedExercise()
            planRepository.nextCreateFailure = TrainingPlanPersistenceError.Unavailable

            val result = createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value))))

            assertEquals(TrainingPlanOperationError.PersistenceUnavailable, requireFailure(result))
        }

    private fun <T> requireSuccess(result: DomainResult<T, TrainingPlanOperationError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailure(result: DomainResult<T, TrainingPlanOperationError>): TrainingPlanOperationError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }

    private fun requireSuccessExercise(result: DomainResult<Exercise, *>): Exercise =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun requireSuccessName(result: DomainResult<ExerciseName, *>): ExerciseName =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
