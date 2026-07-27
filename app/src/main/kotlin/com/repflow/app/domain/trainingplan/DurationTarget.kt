package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult

/** A target duration range in whole seconds, e.g. "30 to 60 seconds". */
@ConsistentCopyVisibility
data class DurationTarget private constructor(
    val minSeconds: Long,
    val maxSeconds: Long,
) {
    companion object {
        const val MIN_SECONDS = 1L
        const val MAX_SECONDS = 7_200L // 2 hours

        fun create(
            minSeconds: Long,
            maxSeconds: Long,
        ): DomainResult<DurationTarget, TrainingPlanValidationError> {
            if (minSeconds < MIN_SECONDS || maxSeconds > MAX_SECONDS || minSeconds > maxSeconds) {
                return DomainResult.Failure(TrainingPlanValidationError.DurationRangeInvalid)
            }
            return DomainResult.Success(DurationTarget(minSeconds, maxSeconds))
        }
    }
}
