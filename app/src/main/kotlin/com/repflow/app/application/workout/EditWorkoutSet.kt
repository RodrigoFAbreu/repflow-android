package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import javax.inject.Inject

/** Raw, UI-shaped input for correcting the set named by [setId]. */
data class EditWorkoutSetCommand(
    val sessionId: WorkoutSessionId,
    val exerciseId: WorkoutExerciseId,
    val setId: WorkoutSetId,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Double?,
    val isWarmup: Boolean,
    val pain: Int? = null,
    val techniqueQuality: Int? = null,
)

/**
 * Corrects a logged set in place (same id, order and `createdAt`), the "edit" half of the fast set-entry flow.
 * A set id that isn't in the exercise is [WorkoutOperationError.NotFound] and nothing is written.
 */
class EditWorkoutSet
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: EditWorkoutSetCommand): DomainResult<Unit, WorkoutOperationError> {
            val session =
                repository.findById(command.sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val exercise =
                session.exercises.firstOrNull { it.id == command.exerciseId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val set =
                exercise.sets.firstOrNull { it.id == command.setId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)

            val updatedSet: WorkoutSet =
                WorkoutSet
                    .create(
                        id = set.id,
                        order = set.order,
                        trackingType = exercise.trackingType,
                        load = command.load,
                        reps = command.reps,
                        durationSeconds = command.durationSeconds,
                        rpe = command.rpe,
                        isWarmup = command.isWarmup,
                        createdAt = set.createdAt,
                        updatedAt = clock.now(),
                        pain = command.pain,
                        techniqueQuality = command.techniqueQuality,
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
