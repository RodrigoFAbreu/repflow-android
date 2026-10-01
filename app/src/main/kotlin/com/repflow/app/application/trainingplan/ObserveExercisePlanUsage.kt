package com.repflow.app.application.trainingplan

import com.repflow.app.domain.exercise.ExerciseId
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The number of non-archived plans whose latest version holds each exercise,
 * reactively - the exercise library's `in N plans` / `not in any plan` meta
 * (remediation-1 CP10, `2c`). A read model over the existing
 * [TrainingPlanRepository]; nothing is stored. Exercises held by no current
 * plan are absent from the map.
 */
class ObserveExercisePlanUsage
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
    ) {
        operator fun invoke(): Flow<Map<ExerciseId, Int>> = repository.observeExercisePlanUsage()
    }
