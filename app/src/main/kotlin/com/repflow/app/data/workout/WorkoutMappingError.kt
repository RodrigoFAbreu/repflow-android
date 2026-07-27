package com.repflow.app.data.workout

import com.repflow.app.domain.workout.WorkoutValidationError

/**
 * A failure reconstructing a domain workout aggregate from persisted rows.
 * Mirrors [com.repflow.app.data.trainingplan.TrainingPlanMappingError]'s
 * "loud, not silent" rationale.
 */
sealed interface WorkoutMappingError {
    val entityId: String

    data class UnknownStatus(
        override val entityId: String,
        val rawValue: String,
    ) : WorkoutMappingError

    data class InvalidFields(
        override val entityId: String,
        val errors: List<WorkoutValidationError>,
    ) : WorkoutMappingError
}

/**
 * Thrown from within [WorkoutEntityMapper]-driven `Flow` transformations so
 * a corrupt row fails the whole observation emission rather than silently
 * dropping it. Mirrors
 * [com.repflow.app.data.trainingplan.TrainingPlanMappingException].
 */
class WorkoutMappingException(
    val mappingError: WorkoutMappingError,
) : IllegalStateException("Workout row ${mappingError.entityId} failed to map from storage: $mappingError")
