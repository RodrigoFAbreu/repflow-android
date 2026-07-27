package com.repflow.app.application.progression

/** Application-facing failure surfaced from progression use cases. */
sealed interface ProgressionOperationError {
    data object NotFound : ProgressionOperationError

    data class PersistenceFailed(
        val cause: ProgressionPersistenceError,
    ) : ProgressionOperationError
}

internal fun ProgressionPersistenceError.toOperationError(): ProgressionOperationError = ProgressionOperationError.PersistenceFailed(this)
