package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/**
 * Marks a completed session invalidated - a correction for a wrongly
 * recorded workout, excluding it from History and future progression input
 * without a destructive delete. Mirrors
 * [com.repflow.app.application.exercise.ArchiveExercise]'s shape.
 */
class InvalidateWorkoutSession
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(sessionId: WorkoutSessionId): DomainResult<Unit, WorkoutOperationError> {
            val existing = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            if (existing.isInvalidated) {
                return DomainResult.Failure(WorkoutOperationError.AlreadyInvalidated)
            }

            val invalidated =
                existing.invalidate(clock.now()).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(invalidated)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
