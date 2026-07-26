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
    }
}
