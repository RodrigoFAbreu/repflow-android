package com.repflow.app.application.trainingplan

import com.repflow.app.domain.trainingplan.TrainingPlanValidationError

/**
 * Failures surfaced by the training-plan use cases to their callers
 * (typically a ViewModel). Mirrors
 * [com.repflow.app.application.exercise.ExerciseOperationError]'s shape.
 */
sealed interface TrainingPlanOperationError {
    data object NotFound : TrainingPlanOperationError

    data object DuplicateName : TrainingPlanOperationError

    data class ValidationFailed(
        val errors: List<TrainingPlanValidationError>,
    ) : TrainingPlanOperationError

    data class PlannedExerciseInvalid(
        val errors: List<PlannedExerciseValidationError>,
    ) : TrainingPlanOperationError

    data object PersistenceUnavailable : TrainingPlanOperationError
}
