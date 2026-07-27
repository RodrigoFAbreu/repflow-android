package com.repflow.app.data.trainingplan

import com.repflow.app.domain.trainingplan.TrainingPlanValidationError

/**
 * A failure reconstructing a domain training-plan aggregate from persisted
 * rows. Mirrors [com.repflow.app.data.exercise.ExerciseMappingError]'s
 * "loud, not silent" rationale.
 */
sealed interface TrainingPlanMappingError {
    val entityId: String

    data class UnknownTargetKind(
        override val entityId: String,
        val rawValue: String,
    ) : TrainingPlanMappingError

    data class InvalidFields(
        override val entityId: String,
        val errors: List<TrainingPlanValidationError>,
    ) : TrainingPlanMappingError
}

/**
 * Thrown from within [TrainingPlanEntityMapper]-driven `Flow`
 * transformations so a corrupt row fails the whole observation emission
 * rather than silently dropping it. Mirrors
 * [com.repflow.app.data.exercise.ExerciseMappingException].
 */
class TrainingPlanMappingException(
    val mappingError: TrainingPlanMappingError,
) : IllegalStateException("Training plan row ${mappingError.entityId} failed to map from storage: $mappingError")
