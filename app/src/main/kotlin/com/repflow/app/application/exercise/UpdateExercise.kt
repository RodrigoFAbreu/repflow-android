package com.repflow.app.application.exercise

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import javax.inject.Inject

/** Raw, UI-shaped input for editing an existing exercise. */
data class UpdateExerciseCommand(
    val id: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val instructions: String?,
    val defaultLoadIncrementGrams: Long?,
    val defaultRestSeconds: Long?,
)

class UpdateExercise
    @Inject
    constructor(
        private val repository: ExerciseRepository,
        private val clock: Clock,
    ) {
        /**
         * Always writes and bumps `updatedAt` when called. Skipping the write
         * for an unchanged draft is the editor's responsibility (it diffs the
         * draft against the loaded exercise before calling this use case), not
         * this use case's.
         *
         * Guard-clause-style early returns keep each failure next to the check
         * that produces it (see [CreateExercise.invoke] for the `@Suppress`
         * rationale).
         */
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: UpdateExerciseCommand): DomainResult<Unit, ExerciseOperationError> {
            val existing =
                repository.findById(command.id) ?: return DomainResult.Failure(ExerciseOperationError.NotFound)

            val fields =
                validateExerciseFields(
                    name = command.name,
                    instructions = command.instructions,
                    loadIncrementGrams = command.defaultLoadIncrementGrams,
                    restSeconds = command.defaultRestSeconds,
                ).getOrElse { errors ->
                    return DomainResult.Failure(ExerciseOperationError.ValidationFailed(errors))
                }

            if (fields.loadIncrement != null && !command.trackingType.supportsLoad) {
                return DomainResult.Failure(
                    ExerciseOperationError.ValidationFailed(listOf(ExerciseValidationError.LoadIncrementNotSupported)),
                )
            }

            val conflictingId = repository.findIdByNameKey(fields.name.key)
            if (conflictingId != null && conflictingId != existing.id) {
                return DomainResult.Failure(ExerciseOperationError.DuplicateName)
            }

            val updated =
                Exercise
                    .reconstruct(
                        id = existing.id,
                        name = fields.name,
                        trackingType = command.trackingType,
                        instructions = fields.instructions,
                        defaultLoadIncrement = fields.loadIncrement,
                        defaultRestDuration = fields.restDuration,
                        origin = existing.origin,
                        archivedAt = existing.archivedAt,
                        createdAt = existing.createdAt,
                        updatedAt = clock.now(),
                    ).getOrElse { errors ->
                        return DomainResult.Failure(ExerciseOperationError.ValidationFailed(errors))
                    }

            return when (val result = repository.update(updated)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
