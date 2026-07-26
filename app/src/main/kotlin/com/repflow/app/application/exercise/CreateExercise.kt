package com.repflow.app.application.exercise

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import javax.inject.Inject

/** Raw, UI-shaped input for creating a new custom exercise. */
data class CreateExerciseCommand(
    val name: String,
    val trackingType: ExerciseTrackingType,
    val instructions: String?,
    val defaultLoadIncrementGrams: Long?,
    val defaultRestSeconds: Long?,
)

class CreateExercise
    @Inject
    constructor(
        private val repository: ExerciseRepository,
        private val clock: Clock,
        private val identifierGenerator: IdentifierGenerator,
    ) {
        /**
         * Guard-clause-style early returns keep each failure (validation, unsupported
         * load, duplicate name) next to the check that produces it. Detekt's
         * `excludeGuardClauses` only recognizes plain `if`/elvis returns, not the
         * [com.repflow.app.domain.common.getOrElse]-based early returns used here, so
         * this is a deliberate, narrow suppression rather than a lowered project-wide
         * threshold.
         */
        @Suppress("ReturnCount")
        suspend operator fun invoke(command: CreateExerciseCommand): DomainResult<ExerciseId, ExerciseOperationError> {
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

            if (repository.findIdByNameKey(fields.name.key) != null) {
                return DomainResult.Failure(ExerciseOperationError.DuplicateName)
            }

            val exercise =
                Exercise
                    .create(
                        id = ExerciseId(identifierGenerator.newId()),
                        name = fields.name,
                        trackingType = command.trackingType,
                        instructions = fields.instructions,
                        defaultLoadIncrement = fields.loadIncrement,
                        defaultRestDuration = fields.restDuration,
                        origin = ExerciseOrigin.CUSTOM,
                        createdAt = clock.now(),
                    ).getOrElse { errors ->
                        return DomainResult.Failure(ExerciseOperationError.ValidationFailed(errors))
                    }

            return when (val result = repository.insert(exercise)) {
                is DomainResult.Success -> DomainResult.Success(exercise.id)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
