package com.repflow.app.application.workout

import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/**
 * Raw, UI-shaped input for adding an exercise (from the source plan version,
 * or ad hoc) to an active session. [plannedExerciseId] is `null` for an ad
 * hoc exercise.
 */
data class AddWorkoutExerciseCommand(
    val sessionId: WorkoutSessionId,
    val exerciseId: ExerciseId,
    val exerciseNameSnapshot: String,
    val trackingType: ExerciseTrackingType,
    val plannedExerciseId: PlannedExerciseId?,
)

class AddWorkoutExercise
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: AddWorkoutExerciseCommand): DomainResult<WorkoutExerciseId, WorkoutOperationError> {
            val session = repository.findById(command.sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)

            val exercise: WorkoutExercise =
                WorkoutExercise
                    .create(
                        id = WorkoutExerciseId(identifierGenerator.newId()),
                        sessionId = session.id,
                        exerciseId = command.exerciseId,
                        order = session.nextExerciseOrder(),
                        exerciseNameSnapshot = command.exerciseNameSnapshot,
                        trackingType = command.trackingType,
                        plannedExerciseId = command.plannedExerciseId,
                    ).getOrElse { error ->
                        return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                    }

            val updatedSession =
                session.withAddedExercise(exercise).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }

            return when (val result = repository.update(updatedSession)) {
                is DomainResult.Success -> DomainResult.Success(exercise.id)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
