package com.repflow.app.application.progression

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ManualOverride
import com.repflow.app.domain.progression.ProgressionResult
import javax.inject.Inject

/** Records a user's [ManualOverride] on the latest recommendation for an exercise. */
class RecordManualOverride
    @Inject
    constructor(
        private val repository: ProgressionRecommendationRepository,
        private val clock: Clock,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(
            exerciseId: ExerciseId,
            overrideResult: ProgressionResult,
        ): DomainResult<Unit, ProgressionOperationError> {
            val latest =
                repository.findLatestForExercise(exerciseId)
                    ?: return DomainResult.Failure(ProgressionOperationError.NotFound)
            val overridden =
                when (val result = latest.withOverride(ManualOverride(overrideResult, clock.now()))) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> return DomainResult.Failure(ProgressionOperationError.NotFound)
                }
            return when (val updateResult = repository.update(overridden)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(updateResult.error.toOperationError())
            }
        }
    }
