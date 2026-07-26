package com.repflow.app.application.exercise

/**
 * A failure translated by the repository from a persistence-layer exception
 * (see the infrastructure/data exception-translation rules). Deliberately
 * narrower than [ExerciseOperationError]: the repository has no concept of
 * "not found" or "already archived" - those are use-case-level concerns
 * decided from an already-loaded [com.repflow.app.domain.exercise.Exercise].
 */
sealed interface ExercisePersistenceError {
    /** The write violated the `name_key` uniqueness constraint. */
    data object DuplicateName : ExercisePersistenceError

    /** Any other persistence failure (disk full, corrupt database, ...). */
    data object Unavailable : ExercisePersistenceError
}

internal fun ExercisePersistenceError.toOperationError(): ExerciseOperationError =
    when (this) {
        ExercisePersistenceError.DuplicateName -> ExerciseOperationError.DuplicateName
        ExercisePersistenceError.Unavailable -> ExerciseOperationError.PersistenceUnavailable
    }
