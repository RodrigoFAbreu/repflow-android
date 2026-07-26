package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import java.time.Instant

/**
 * A trackable exercise definition (not a performed set or workout).
 *
 * The only public creation paths are [create] (for a brand-new exercise) and
 * [reconstruct] (for rebuilding an already-validated instance from
 * persistence). The primary constructor is private and `@ConsistentCopyVisibility`
 * forces the generated `copy()` to be private too, so even [archive] and
 * [restore] must be implemented as member functions rather than by callers
 * reaching for `copy()` directly.
 */
@ConsistentCopyVisibility
data class Exercise private constructor(
    val id: ExerciseId,
    val name: ExerciseName,
    val trackingType: ExerciseTrackingType,
    val instructions: ExerciseInstructions?,
    val defaultLoadIncrement: LoadIncrement?,
    val defaultRestDuration: RestDuration?,
    val origin: ExerciseOrigin,
    val archivedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isArchived: Boolean get() = archivedAt != null

    /** Returns a new, archived instance. Never changes [createdAt] or [origin]. */
    fun archive(at: Instant): Exercise = copy(archivedAt = at, updatedAt = at)

    /** Returns a new, active instance. Never changes [createdAt] or [origin]. */
    fun restore(at: Instant): Exercise = copy(archivedAt = null, updatedAt = at)

    companion object {
        /**
         * Creates a brand-new, active exercise with `createdAt == updatedAt`.
         *
         * Every field of the aggregate is required to construct one;
         * grouping them into a parameter object would only move the
         * [Suppress]-worthy parameter count to a type with no other
         * consumer.
         */
        @Suppress("LongParameterList")
        fun create(
            id: ExerciseId,
            name: ExerciseName,
            trackingType: ExerciseTrackingType,
            instructions: ExerciseInstructions?,
            defaultLoadIncrement: LoadIncrement?,
            defaultRestDuration: RestDuration?,
            origin: ExerciseOrigin,
            createdAt: Instant,
        ): DomainResult<Exercise, List<ExerciseValidationError>> =
            reconstruct(
                id = id,
                name = name,
                trackingType = trackingType,
                instructions = instructions,
                defaultLoadIncrement = defaultLoadIncrement,
                defaultRestDuration = defaultRestDuration,
                origin = origin,
                archivedAt = null,
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        /**
         * Rebuilds an exercise from already-persisted, previously-validated
         * field values, re-checking every cross-field invariant. Used by the
         * infrastructure/data mapper so a corrupted row fails loudly instead
         * of silently bypassing domain invariants. See [create] for the
         * [Suppress] justification.
         */
        @Suppress("LongParameterList")
        fun reconstruct(
            id: ExerciseId,
            name: ExerciseName,
            trackingType: ExerciseTrackingType,
            instructions: ExerciseInstructions?,
            defaultLoadIncrement: LoadIncrement?,
            defaultRestDuration: RestDuration?,
            origin: ExerciseOrigin,
            archivedAt: Instant?,
            createdAt: Instant,
            updatedAt: Instant,
        ): DomainResult<Exercise, List<ExerciseValidationError>> {
            val errors = mutableListOf<ExerciseValidationError>()
            if (defaultLoadIncrement != null && !trackingType.supportsLoad) {
                errors += ExerciseValidationError.LoadIncrementNotSupported
            }
            if (updatedAt < createdAt) {
                errors += ExerciseValidationError.UpdatedBeforeCreated
            }
            if (archivedAt != null && archivedAt < createdAt) {
                errors += ExerciseValidationError.ArchivedBeforeCreated
            }
            if (errors.isNotEmpty()) {
                return DomainResult.Failure(errors)
            }
            return DomainResult.Success(
                Exercise(
                    id = id,
                    name = name,
                    trackingType = trackingType,
                    instructions = instructions,
                    defaultLoadIncrement = defaultLoadIncrement,
                    defaultRestDuration = defaultRestDuration,
                    origin = origin,
                    archivedAt = archivedAt,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                ),
            )
        }
    }
}
