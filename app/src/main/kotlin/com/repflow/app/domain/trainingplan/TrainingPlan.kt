package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import java.time.Instant

/**
 * A reusable, versioned workout template.
 *
 * This aggregate deliberately does **not** hold a pointer to its "current"
 * or "latest" [TrainingPlanVersion] - which version is current is a query
 * concept (`max(version_number)` for this plan's id), not state carried on
 * the plan itself. Keeping it out of this type means [TrainingPlan] can
 * never be constructed in a state that disagrees with its own versions.
 *
 * The only public creation paths are [create] and [reconstruct], following
 * the same private-constructor + `@ConsistentCopyVisibility` shape as
 * [com.repflow.app.domain.exercise.Exercise].
 */
@ConsistentCopyVisibility
data class TrainingPlan private constructor(
    val id: TrainingPlanId,
    val name: TrainingPlanName,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun create(
            id: TrainingPlanId,
            name: TrainingPlanName,
            createdAt: Instant,
        ): DomainResult<TrainingPlan, TrainingPlanValidationError> =
            reconstruct(id = id, name = name, createdAt = createdAt, updatedAt = createdAt)

        fun reconstruct(
            id: TrainingPlanId,
            name: TrainingPlanName,
            createdAt: Instant,
            updatedAt: Instant,
        ): DomainResult<TrainingPlan, TrainingPlanValidationError> {
            if (updatedAt < createdAt) {
                return DomainResult.Failure(TrainingPlanValidationError.UpdatedBeforeCreated)
            }
            return DomainResult.Success(
                TrainingPlan(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt),
            )
        }
    }
}
