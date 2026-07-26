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
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class RestoreTrainingPlanTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val archivedAt = Instant.parse("2026-01-02T00:00:00Z")
    private val restoredAt = Instant.parse("2026-01-03T00:00:00Z")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan =
        CreateTrainingPlan(planRepository, exerciseRepository, FixedClock(createdAt), SequentialIdentifierGenerator("plan"))
    private val archiveTrainingPlan = ArchiveTrainingPlan(planRepository, FixedClock(archivedAt))
    private val restoreTrainingPlan = RestoreTrainingPlan(planRepository, FixedClock(restoredAt))

    private suspend fun seedArchivedPlan(): TrainingPlanId {
        val exercise =
            requireSuccessDomain(
                Exercise.create(
                    id = ExerciseId("bench-press"),
                    name = requireSuccessDomain(ExerciseName.create("Bench Press")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            )
        exerciseRepository.seed(exercise)
        val command =
            CreateTrainingPlanCommand(
                name = "Push Day",
                plannedExercises =
                    listOf(
                        PlannedExerciseInput(
                            exerciseId = exercise.id.value,
                            order = 0,
                            targetSets = 3,
                            targetKind = PlannedExerciseTargetKind.REPS,
                            repMin = 8,
                            repMax = 12,
                            durationMinSeconds = null,
                            durationMaxSeconds = null,
                            restSeconds = 90,
                            isOptional = false,
                        ),
                    ),
            )
        val planId = requireSuccessOp(createTrainingPlan(command))
        requireSuccessOp(archiveTrainingPlan(planId))
        return planId
    }

    @Test
    fun `restores an archived plan and bumps updatedAt`() =
        runTest {
            val planId = seedArchivedPlan()

            val result = restoreTrainingPlan(planId)

            requireSuccessOp(result)
            val stored = requireNotNull(planRepository.findOverviewByPlanId(planId)).plan
            assertFalse(stored.isArchived)
            assertEquals(restoredAt, stored.updatedAt)
            assertEquals(createdAt, stored.createdAt)
        }

    @Test
    fun `restoring an already-active plan is an idempotent no-op`() =
        runTest {
            val planId = seedArchivedPlan()
            requireSuccessOp(restoreTrainingPlan(planId))

            val result = restoreTrainingPlan(planId)

            requireSuccessOp(result)
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = restoreTrainingPlan(TrainingPlanId("missing"))

            assertEquals(TrainingPlanOperationError.NotFound, requireFailureOp(result))
        }

    private fun <T> requireSuccessDomain(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireSuccessOp(result: DomainResult<T, TrainingPlanOperationError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailureOp(result: DomainResult<T, TrainingPlanOperationError>): TrainingPlanOperationError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }
}
