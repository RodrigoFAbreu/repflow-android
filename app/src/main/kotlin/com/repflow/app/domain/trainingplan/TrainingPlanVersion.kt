package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import java.time.Instant

/**
 * An immutable version of a [TrainingPlan], capturing its ordered planned
 * exercises and targets at a point in time.
 *
 * There is deliberately no "edit a version" operation on this type -
 * revising a plan always produces a brand-new [TrainingPlanVersion] with an
 * incremented [versionNumber] (see the application layer's
 * `ReviseTrainingPlan`). Existing version rows are never mutated once
 * created, which is what lets future workout history reference a specific
 * version safely.
 */
@ConsistentCopyVisibility
data class TrainingPlanVersion private constructor(
    val id: TrainingPlanVersionId,
    val planId: TrainingPlanId,
    val versionNumber: Int,
    val plannedExercises: List<PlannedExercise>,
    val note: String?,
    val createdAt: Instant,
) {
    companion object {
        /**
         * Validates that [plannedExercises] is non-empty and that its
         * `order` values are exactly `0 until size`, with no gaps or
         * repeats, before constructing the version. Every field is
         * required to construct a version; grouping them into a parameter
         * object would only move the [Suppress]-worthy parameter count to a
         * type with no other consumer (see [com.repflow.app.domain.exercise.Exercise.create]
         * for the same rationale).
         */
        @Suppress("LongParameterList")
        fun create(
            id: TrainingPlanVersionId,
            planId: TrainingPlanId,
            versionNumber: Int,
            plannedExercises: List<PlannedExercise>,
            note: String?,
            createdAt: Instant,
        ): DomainResult<TrainingPlanVersion, TrainingPlanValidationError> {
            if (versionNumber < 1) {
                return DomainResult.Failure(TrainingPlanValidationError.VersionNumberInvalid)
            }
            if (plannedExercises.isEmpty()) {
                return DomainResult.Failure(TrainingPlanValidationError.NoPlannedExercises)
            }
            val expectedOrders = 0 until plannedExercises.size
            val actualOrders = plannedExercises.map { it.order }.toSet()
            if (actualOrders != expectedOrders.toSet()) {
                return DomainResult.Failure(TrainingPlanValidationError.InvalidOrderSequence)
            }
            return DomainResult.Success(
                TrainingPlanVersion(
                    id = id,
                    planId = planId,
                    versionNumber = versionNumber,
                    plannedExercises = plannedExercises.sortedBy { it.order },
                    note = note,
                    createdAt = createdAt,
                ),
            )
        }
    }
}
