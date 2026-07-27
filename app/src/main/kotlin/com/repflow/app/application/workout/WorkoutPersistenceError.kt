package com.repflow.app.application.workout

/**
 * A failure translated by the repository from a persistence-layer
 * exception. Mirrors
 * [com.repflow.app.application.trainingplan.TrainingPlanPersistenceError]'s
 * rationale exactly.
 */
sealed interface WorkoutPersistenceError {
    /** An insert was attempted while another session is already active. */
    data object ActiveSessionAlreadyExists : WorkoutPersistenceError

    /** An update targeted a session id that no longer exists. */
    data object NotFound : WorkoutPersistenceError

    /** Any other persistence failure (disk full, corrupt database, ...). */
    data object Unavailable : WorkoutPersistenceError
}

internal fun WorkoutPersistenceError.toOperationError(): WorkoutOperationError =
    when (this) {
        WorkoutPersistenceError.ActiveSessionAlreadyExists -> WorkoutOperationError.ActiveSessionAlreadyExists
        WorkoutPersistenceError.NotFound -> WorkoutOperationError.NotFound
        WorkoutPersistenceError.Unavailable -> WorkoutOperationError.PersistenceUnavailable
    }
