package com.repflow.app.application.trainingplan

/**
 * A failure translated by the repository from a persistence-layer
 * exception. Mirrors
 * [com.repflow.app.application.exercise.ExercisePersistenceError]'s
 * rationale exactly.
 */
sealed interface TrainingPlanPersistenceError {
    /** The write violated the plan `name_key` uniqueness constraint. */
    data object DuplicateName : TrainingPlanPersistenceError

    /** Any other persistence failure (disk full, corrupt database, ...). */
    data object Unavailable : TrainingPlanPersistenceError
}

internal fun TrainingPlanPersistenceError.toOperationError(): TrainingPlanOperationError =
    when (this) {
        TrainingPlanPersistenceError.DuplicateName -> TrainingPlanOperationError.DuplicateName
        TrainingPlanPersistenceError.Unavailable -> TrainingPlanOperationError.PersistenceUnavailable
    }
