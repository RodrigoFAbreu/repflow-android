package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import javax.inject.Inject

/** Raw, UI-shaped input for editing the most recently recorded set's values. */
data class EditLastWorkoutSetCommand(
    val sessionId: WorkoutSessionId,
    val exerciseId: WorkoutExerciseId,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Double?,
    val isWarmup: Boolean,
)

/** Edits the most recently recorded set in place (same id and order), the "edit" half of the fast set-entry flow. */
class EditLastWorkoutSet
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: EditLastWorkoutSetCommand): DomainResult<Unit, WorkoutOperationError> {
            val session =
                repository.findById(command.sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val exercise =
                session.exercises.firstOrNull { it.id == command.exerciseId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val lastSet = exercise.sets.maxByOrNull { it.order } ?: return DomainResult.Failure(WorkoutOperationError.NotFound)

            val updatedSet: WorkoutSet =
                WorkoutSet
                    .create(
                        id = lastSet.id,
                        order = lastSet.order,
                        trackingType = exercise.trackingType,
                        load = command.load,
                        reps = command.reps,
                        durationSeconds = command.durationSeconds,
                        rpe = command.rpe,
                        isWarmup = command.isWarmup,
                        createdAt = lastSet.createdAt,
                        updatedAt = clock.now(),
                    ).getOrElse { error ->
                        return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                    }

            val updatedExercise =
                exercise.withUpdatedSet(updatedSet).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
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
