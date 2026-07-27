package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult

/** The number of target working sets for a [PlannedExercise]. */
@JvmInline
value class TargetSets private constructor(
    val value: Int,
) {
    companion object {
        const val MIN = 1
        const val MAX = 20

        fun create(value: Int): DomainResult<TargetSets, TrainingPlanValidationError> {
            if (value < MIN || value > MAX) {
                return DomainResult.Failure(TrainingPlanValidationError.TargetSetsOutOfRange)
            }
            return DomainResult.Success(TargetSets(value))
        }

        /**
         * Validates a [PlannedExercise.targetWarmupSets] value: `null` ("no warm-up
         * guidance") is always valid; a non-null value must be a non-negative int up
         * to [MAX] (unlike [create]'s working-sets range, 0 is a legal value here -
         * "planned zero warm-up sets" is meaningful, `null` means "unspecified").
         */
        fun validateWarmupSets(value: Int?): TrainingPlanValidationError? {
            if (value != null && (value < 0 || value > MAX)) {
                return TrainingPlanValidationError.TargetWarmupSetsOutOfRange
            }
            return null
        }
    }
}
