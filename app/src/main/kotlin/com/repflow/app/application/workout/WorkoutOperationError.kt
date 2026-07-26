package com.repflow.app.application.workout

import com.repflow.app.domain.workout.WorkoutValidationError

/**
 * Failures surfaced by the workout use cases to their callers (typically a
 * ViewModel). Mirrors
 * [com.repflow.app.application.trainingplan.TrainingPlanOperationError]'s
 * shape.
 */
sealed interface WorkoutOperationError {
    data object NotFound : WorkoutOperationError

    /** A new session was requested while one is already active (single-active-session invariant). */
    data object ActiveSessionAlreadyExists : WorkoutOperationError

    data class ValidationFailed(
        val errors: List<WorkoutValidationError>,
    ) : WorkoutOperationError

    data object PersistenceUnavailable : WorkoutOperationError
}
