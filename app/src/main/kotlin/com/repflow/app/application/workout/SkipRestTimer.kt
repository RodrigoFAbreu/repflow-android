package com.repflow.app.application.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

class SkipRestTimer
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        /** Clears the active session's rest timer, immediately persisted. A no-op (still success) if none is running. */
        @Suppress("ReturnCount")
        suspend operator fun invoke(sessionId: WorkoutSessionId): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val updated =
                session.withClearedRestTimer().getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(updated)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
