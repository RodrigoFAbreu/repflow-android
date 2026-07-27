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
    val archivedAt: Instant?,
) {
    val isArchived: Boolean get() = archivedAt != null

    /** Returns a new, archived instance. Never changes [createdAt]. */
    fun archive(at: Instant): TrainingPlan = copy(archivedAt = at, updatedAt = at)

    /** Returns a new, active instance. Never changes [createdAt]. */
    fun restore(at: Instant): TrainingPlan = copy(archivedAt = null, updatedAt = at)

    companion object {
        fun create(
            id: TrainingPlanId,
            name: TrainingPlanName,
            createdAt: Instant,
        ): DomainResult<TrainingPlan, TrainingPlanValidationError> =
            reconstruct(id = id, name = name, createdAt = createdAt, updatedAt = createdAt, archivedAt = null)

        fun reconstruct(
            id: TrainingPlanId,
            name: TrainingPlanName,
            createdAt: Instant,
            updatedAt: Instant,
            archivedAt: Instant? = null,
        ): DomainResult<TrainingPlan, TrainingPlanValidationError> {
            if (updatedAt < createdAt) {
                return DomainResult.Failure(TrainingPlanValidationError.UpdatedBeforeCreated)
            }
            if (archivedAt != null && archivedAt < createdAt) {
                return DomainResult.Failure(TrainingPlanValidationError.ArchivedBeforeCreated)
            }
            return DomainResult.Success(
                TrainingPlan(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt, archivedAt = archivedAt),
            )
        }
    }
}
