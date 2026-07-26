package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import javax.inject.Inject

class GetExercise
    @Inject
    constructor(
        private val repository: ExerciseRepository,
    ) {
        suspend operator fun invoke(id: ExerciseId): DomainResult<Exercise, ExerciseOperationError> {
            val exercise = repository.findById(id) ?: return DomainResult.Failure(ExerciseOperationError.NotFound)
            return DomainResult.Success(exercise)
        }
    }
