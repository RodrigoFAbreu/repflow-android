package com.repflow.app.application.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Removes the most recently recorded set from an exercise (the "undo" half of the fast set-entry flow). */
class UndoLastWorkoutSet
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(
            sessionId: WorkoutSessionId,
            exerciseId: WorkoutExerciseId,
        ): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val exercise =
                session.exercises.firstOrNull { it.id == exerciseId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val lastSet = exercise.sets.maxByOrNull { it.order } ?: return DomainResult.Failure(WorkoutOperationError.NotFound)

            val updatedExercise = exercise.withoutSet(lastSet.id)
            val updatedSession =
                session.withUpdatedExercise(updatedExercise).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }

            return when (val result = repository.update(updatedSession)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
