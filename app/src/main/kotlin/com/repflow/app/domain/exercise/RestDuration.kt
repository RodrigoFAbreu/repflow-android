package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult

/**
 * A default rest duration, stored as whole seconds.
 */
@JvmInline
value class RestDuration private constructor(
    val seconds: Long,
) {
    companion object {
        const val MIN_SECONDS = 1L
        const val MAX_SECONDS = 1_800L // 30 minutes

        fun create(seconds: Long): DomainResult<RestDuration, ExerciseValidationError> {
            if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
                return DomainResult.Failure(ExerciseValidationError.RestDurationOutOfRange)
            }
            return DomainResult.Success(RestDuration(seconds))
        }
    }
}
