package com.repflow.app.application.trainingplan

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanId
import javax.inject.Inject

class GetTrainingPlanDetail
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
    ) {
        suspend operator fun invoke(id: TrainingPlanId): DomainResult<TrainingPlanOverview, TrainingPlanOperationError> {
            val overview =
                repository.findOverviewByPlanId(id) ?: return DomainResult.Failure(TrainingPlanOperationError.NotFound)
            return DomainResult.Success(overview)
        }
    }
