package com.repflow.app.application.exercise

import com.repflow.app.domain.exercise.ExerciseValidationError

/**
 * Failures surfaced by the exercise use cases to their callers (typically a
 * ViewModel).
 *
 * There is deliberately no `NotArchived` case: restoring an already-active
 * exercise is treated as an idempotent no-op (see [RestoreExercise]), not a
 * failure.
 */
sealed interface ExerciseOperationError {
    data object NotFound : ExerciseOperationError

    data object AlreadyArchived : ExerciseOperationError

    data object DuplicateName : ExerciseOperationError

    data class ValidationFailed(
        val errors: List<ExerciseValidationError>,
    ) : ExerciseOperationError

    data object PersistenceUnavailable : ExerciseOperationError
}
