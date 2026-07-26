package com.repflow.app.domain.trainingplan

/**
 * What a [PlannedExercise] is targeting: repetitions or duration, never
 * both and never neither.
 *
 * Modelled as a sealed type rather than a pair of nullable fields so
 * "exactly one of reps or duration" is a structural guarantee instead of an
 * invariant that must be checked at every call site.
 */
sealed interface PlannedExerciseTarget {
    data class Reps(
        val range: RepRange,
    ) : PlannedExerciseTarget

    data class Duration(
        val range: DurationTarget,
    ) : PlannedExerciseTarget
}
