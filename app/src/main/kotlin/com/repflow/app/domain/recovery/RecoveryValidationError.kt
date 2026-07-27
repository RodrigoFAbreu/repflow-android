package com.repflow.app.domain.recovery

/**
 * Validation failures raised while constructing a [RecoveryEntry].
 */
sealed interface RecoveryValidationError {
    /** One of the 0..4 scale fields (sleep quality, energy, DOMS, ...) is out of range. */
    data object ScaleValueOutOfRange : RecoveryValidationError

    /** The optional notes text exceeds [RecoveryEntry.NOTES_MAX_LENGTH] characters. */
    data object NotesTooLong : RecoveryValidationError

    /** `updatedAt` is before `createdAt`. */
    data object UpdatedBeforeCreated : RecoveryValidationError
}
