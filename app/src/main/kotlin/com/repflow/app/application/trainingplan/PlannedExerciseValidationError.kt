package com.repflow.app.application.trainingplan

/**
 * A single planned-exercise row failed to cross-validate against the
 * exercise it references, once its own fields already passed domain
 * validation. [index] identifies which row in the submitted list failed,
 * so the editor can attach the error to the right row.
 */
sealed interface PlannedExerciseValidationError {
    /** No exercise exists with the given id (it may have been removed from the picker meanwhile). */
    data class ExerciseNotFound(
        val index: Int,
    ) : PlannedExerciseValidationError

    /**
     * The row's [com.repflow.app.domain.trainingplan.PlannedExerciseTarget] kind
     * does not match the referenced exercise's
     * [com.repflow.app.domain.exercise.ExerciseTrackingType] (`WEIGHT_AND_REPS`/`REPS_ONLY`
     * require `Reps`; `DURATION` requires `Duration`).
     */
    data class TargetKindMismatch(
        val index: Int,
    ) : PlannedExerciseValidationError
}
