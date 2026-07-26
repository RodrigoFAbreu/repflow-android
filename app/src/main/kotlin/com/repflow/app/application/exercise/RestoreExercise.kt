package com.repflow.app.application.exercise

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import javax.inject.Inject

/**
 * Restores an archived exercise, backing both the "Restore" action from the
 * archived filter and the archive snackbar's "Undo" action.
 *
 * Idempotent by design (approved implementation correction #8): restoring an
 * exercise that is already active is treated as successfully satisfied - a
 * neutral no-op - rather than a failure. There is deliberately no
 * `NotArchived` case in [ExerciseOperationError].
 */
class RestoreExercise
    @Inject
    constructor(
        private val repository: ExerciseRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(id: ExerciseId): DomainResult<Unit, ExerciseOperationError> {
            val existing = repository.findById(id) ?: return DomainResult.Failure(ExerciseOperationError.NotFound)
            if (!existing.isArchived) {
                return DomainResult.Success(Unit)
            }

            val restored = existing.restore(clock.now())
            return when (val result = repository.update(restored)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
