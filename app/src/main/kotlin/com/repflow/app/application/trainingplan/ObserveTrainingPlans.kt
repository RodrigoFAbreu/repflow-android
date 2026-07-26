package com.repflow.app.application.trainingplan

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveTrainingPlans
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
    ) {
        operator fun invoke(): Flow<List<TrainingPlanOverview>> = repository.observeOverviews()
    }
