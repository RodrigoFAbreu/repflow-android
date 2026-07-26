package com.repflow.app.application.trainingplan

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanId
import javax.inject.Inject

/** Archives a training plan, excluding it from the start-workout plan picker. Mirrors [com.repflow.app.application.exercise.ArchiveExercise]. */
class ArchiveTrainingPlan
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(id: TrainingPlanId): DomainResult<Unit, TrainingPlanOperationError> {
            val overview = repository.findOverviewByPlanId(id) ?: return DomainResult.Failure(TrainingPlanOperationError.NotFound)
            if (overview.plan.isArchived) {
                return DomainResult.Failure(TrainingPlanOperationError.AlreadyArchived)
            }

            val archived = overview.plan.archive(clock.now())
            return when (val result = repository.updatePlan(archived)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
