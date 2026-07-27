package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.RestDuration
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TrainingPlanVersionTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val versionId = TrainingPlanVersionId("version-1")
    private val planId = TrainingPlanId("plan-1")

    private fun plannedExercise(
        order: Int,
        exerciseSuffix: String = order.toString(),
    ) = PlannedExercise(
        id = PlannedExerciseId("planned-$exerciseSuffix"),
        exerciseId = ExerciseId("exercise-$exerciseSuffix"),
        order = order,
        targetSets = requireSuccess(TargetSets.create(3)),
        target = PlannedExerciseTarget.Reps(requireSuccess(RepRange.create(8, 12))),
        restDuration = requireSuccess(RestDuration.create(90)),
        isOptional = false,
    )

    @Test
    fun `create rejects a version number below 1`() {
        val result =
            TrainingPlanVersion.create(
                id = versionId,
                planId = planId,
                versionNumber = 0,
                plannedExercises = listOf(plannedExercise(0)),
                note = null,
                createdAt = createdAt,
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.VersionNumberInvalid), result)
    }

    @Test
    fun `create rejects an empty list of planned exercises`() {
        val result =
            TrainingPlanVersion.create(
                id = versionId,
                planId = planId,
                versionNumber = 1,
                plannedExercises = emptyList(),
                note = null,
                createdAt = createdAt,
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.NoPlannedExercises), result)
    }

    @Test
    fun `create rejects a gap in the order sequence`() {
        val result =
            TrainingPlanVersion.create(
                id = versionId,
                planId = planId,
                versionNumber = 1,
                plannedExercises = listOf(plannedExercise(0), plannedExercise(2, "b")),
                note = null,
                createdAt = createdAt,
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.InvalidOrderSequence), result)
    }

    @Test
    fun `create rejects a duplicate order value`() {
        val result =
            TrainingPlanVersion.create(
                id = versionId,
                planId = planId,
                versionNumber = 1,
                plannedExercises = listOf(plannedExercise(0), plannedExercise(0, "b")),
                note = null,
                createdAt = createdAt,
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.InvalidOrderSequence), result)
    }

    @Test
    fun `create accepts a contiguous zero-based order sequence and sorts by order`() {
        val second = plannedExercise(1, "b")
        val first = plannedExercise(0, "a")

        val version =
            requireSuccess(
                TrainingPlanVersion.create(
                    id = versionId,
                    planId = planId,
                    versionNumber = 1,
                    plannedExercises = listOf(second, first),
                    note = null,
                    createdAt = createdAt,
                ),
            )

        assertEquals(listOf(first, second), version.plannedExercises)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
