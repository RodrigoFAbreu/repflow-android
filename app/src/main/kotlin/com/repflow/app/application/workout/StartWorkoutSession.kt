package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Raw, UI-shaped input for starting a new workout session. */
data class StartWorkoutSessionCommand(
    val trainingPlanVersionId: TrainingPlanVersionId?,
)

class StartWorkoutSession
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        /** Rejects starting a new session while one is already active (see the reference doc's single-active-session invariant). */
        suspend operator fun invoke(command: StartWorkoutSessionCommand): DomainResult<WorkoutSessionId, WorkoutOperationError> {
            if (repository.findActiveSession() != null) {
                return DomainResult.Failure(WorkoutOperationError.ActiveSessionAlreadyExists)
            }
            val session: WorkoutSession =
                WorkoutSession.start(
                    id = WorkoutSessionId(identifierGenerator.newId()),
                    trainingPlanVersionId = command.trainingPlanVersionId,
                    startedAt = clock.now(),
                )
            return when (val result = repository.insert(session)) {
                is DomainResult.Success -> DomainResult.Success(session.id)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
