package com.repflow.app.application.exercise

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import javax.inject.Inject

class ArchiveExercise
    @Inject
    constructor(
        private val repository: ExerciseRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(id: ExerciseId): DomainResult<Unit, ExerciseOperationError> {
            val existing = repository.findById(id) ?: return DomainResult.Failure(ExerciseOperationError.NotFound)
            if (existing.isArchived) {
                return DomainResult.Failure(ExerciseOperationError.AlreadyArchived)
            }

            val archived = existing.archive(clock.now())
            return when (val result = repository.update(archived)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
