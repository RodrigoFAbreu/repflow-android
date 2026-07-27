package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult

/** A target repetition range, e.g. "8 to 12 reps". */
@ConsistentCopyVisibility
data class RepRange private constructor(
    val min: Int,
    val max: Int,
) {
    companion object {
        const val MIN_REPS = 1
        const val MAX_REPS = 999

        fun create(
            min: Int,
            max: Int,
        ): DomainResult<RepRange, TrainingPlanValidationError> {
            if (min < MIN_REPS || max > MAX_REPS || min > max) {
                return DomainResult.Failure(TrainingPlanValidationError.RepRangeInvalid)
            }
            return DomainResult.Success(RepRange(min, max))
        }
    }
}
