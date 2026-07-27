package com.repflow.app.application.recovery

import com.repflow.app.domain.recovery.FutsalValidationError
import com.repflow.app.domain.recovery.RecoveryValidationError

/** Failures surfaced by the recovery use cases to their callers (typically a ViewModel). */
sealed interface RecoveryOperationError {
    data class ValidationFailed(
        val error: RecoveryValidationError,
    ) : RecoveryOperationError

    data object PersistenceUnavailable : RecoveryOperationError
}

/** Mirrors [RecoveryOperationError] for futsal-session use cases. */
sealed interface FutsalOperationError {
    data class ValidationFailed(
        val error: FutsalValidationError,
    ) : FutsalOperationError

    data object PersistenceUnavailable : FutsalOperationError
}
