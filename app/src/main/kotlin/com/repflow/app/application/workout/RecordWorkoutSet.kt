package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import javax.inject.Inject

/** Raw, UI-shaped input for recording a new set against a [com.repflow.app.domain.workout.WorkoutExercise]. */
data class RecordWorkoutSetCommand(
    val sessionId: WorkoutSessionId,
    val exerciseId: WorkoutExerciseId,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Double?,
    val isWarmup: Boolean,
)

class RecordWorkoutSet
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        /** Appends a new set - immediately persisted, per the "important workout state is persisted immediately" invariant. */
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: RecordWorkoutSetCommand): DomainResult<WorkoutSetId, WorkoutOperationError> {
            val session = repository.findById(command.sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val exercise =
                session.exercises.firstOrNull { it.id == command.exerciseId }
                    ?: return DomainResult.Failure(WorkoutOperationError.NotFound)

            val now = clock.now()
            val set: WorkoutSet =
                WorkoutSet
                    .create(
                        id = WorkoutSetId(identifierGenerator.newId()),
                        order = exercise.nextSetOrder(),
                        trackingType = exercise.trackingType,
                        load = command.load,
                        reps = command.reps,
                        durationSeconds = command.durationSeconds,
                        rpe = command.rpe,
                        isWarmup = command.isWarmup,
                        createdAt = now,
                        updatedAt = now,
                    ).getOrElse { error ->
                        return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                    }

            val updatedExercise =
                exercise.withRecordedSet(set).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }

            val updatedSession =
                session.withUpdatedExercise(updatedExercise).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }

            return when (val result = repository.update(updatedSession)) {
                is DomainResult.Success -> DomainResult.Success(set.id)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
