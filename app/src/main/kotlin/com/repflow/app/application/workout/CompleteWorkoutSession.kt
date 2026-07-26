package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Edits/undo of individual sets happen via [RecordWorkoutSet] and repository updates directly from the presentation layer's ViewModel; this use case only handles the terminal transition. */
class CompleteWorkoutSession
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(sessionId: WorkoutSessionId): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val completed =
                session.complete(clock.now()).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(completed)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
