package com.repflow.app.data.exercise

import com.repflow.app.domain.exercise.ExerciseValidationError

/**
 * A failure reconstructing a domain [com.repflow.app.domain.exercise.Exercise]
 * from a persisted [com.repflow.app.infrastructure.database.exercise.ExerciseEntity]
 * row. Every case carries the affected exercise id as diagnostic context
 * (see plan.md D-28).
 *
 * An unrecognised `tracking_type` or `origin` is the specific failure D-28
 * calls out - never a silent default or a skipped row. [InvalidFields]
 * additionally covers the (expected to be unreachable in practice) case of a
 * row that otherwise fails a domain invariant, for the same "loud, not
 * silent" reason.
 */
sealed interface ExerciseMappingError {
    val exerciseId: String

    data class UnknownTrackingType(
        override val exerciseId: String,
        val rawValue: String,
    ) : ExerciseMappingError

    data class UnknownOrigin(
        override val exerciseId: String,
        val rawValue: String,
    ) : ExerciseMappingError

    data class InvalidFields(
        override val exerciseId: String,
        val errors: List<ExerciseValidationError>,
    ) : ExerciseMappingError
}

/**
 * Thrown from within [ExerciseEntityMapper]-driven `Flow` transformations so
 * a corrupt row fails the whole observation emission (D-28) rather than
 * silently dropping it. Caught and translated to a retryable observation
 * failure by the presentation layer (see plan.md section H) - this
 * exception type itself is not part of the application/presentation
 * contract surface.
 */
class ExerciseMappingException(
    val mappingError: ExerciseMappingError,
) : IllegalStateException("Exercise ${mappingError.exerciseId} failed to map from storage: $mappingError")
