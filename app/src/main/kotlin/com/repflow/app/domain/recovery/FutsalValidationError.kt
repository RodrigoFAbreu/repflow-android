package com.repflow.app.domain.recovery

/**
 * Validation failures raised while constructing a [FutsalSession].
 */
sealed interface FutsalValidationError {
    /** `durationMinutes` is not positive. */
    data object DurationNotPositive : FutsalValidationError

    /** `sessionRpe` is outside the supported `0.0..10.0` range. */
    data object SessionRpeOutOfRange : FutsalValidationError

    /** `updatedAt` is before `createdAt`. */
    data object UpdatedBeforeCreated : FutsalValidationError
}
