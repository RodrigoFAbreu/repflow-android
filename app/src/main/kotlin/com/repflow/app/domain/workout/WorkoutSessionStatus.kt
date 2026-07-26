package com.repflow.app.domain.workout

/**
 * Lifecycle state of a [WorkoutSession].
 *
 * Persisted by this stable [name] string, never by ordinal, mirroring
 * [com.repflow.app.domain.exercise.ExerciseTrackingType]'s rationale. Only
 * an [ACTIVE] session may be mutated; [COMPLETED] and [ABANDONED] are
 * terminal.
 */
enum class WorkoutSessionStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED,
}
