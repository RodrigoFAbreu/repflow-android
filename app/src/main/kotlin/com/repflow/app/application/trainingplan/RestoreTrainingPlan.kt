package com.repflow.app.application.trainingplan

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanId
import javax.inject.Inject

/**
 * Restores an archived training plan, backing both the "Restore" action from
 * the archived filter and the archive snackbar's "Undo" action.
 *
 * Idempotent by design, mirroring
 * [com.repflow.app.application.exercise.RestoreExercise]: restoring an
 * already-active plan is a neutral no-op, not a failure. There is
 * deliberately no `NotArchived` case in [TrainingPlanOperationError].
 */
class RestoreTrainingPlan
    @Inject
    constructor(
        private val repository: TrainingPlanRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(id: TrainingPlanId): DomainResult<Unit, TrainingPlanOperationError> {
            val overview = repository.findOverviewByPlanId(id) ?: return DomainResult.Failure(TrainingPlanOperationError.NotFound)
            if (!overview.plan.isArchived) {
                return DomainResult.Success(Unit)
            }

            val restored = overview.plan.restore(clock.now())
            return when (val result = repository.updatePlan(restored)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
