package com.repflow.app.domain.workout

/** Stable identifier for a persisted [WorkoutExercise]. */
@JvmInline
value class WorkoutExerciseId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "WorkoutExerciseId must not be blank" }
    }
}
