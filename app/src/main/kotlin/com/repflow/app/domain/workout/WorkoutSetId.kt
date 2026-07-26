package com.repflow.app.domain.workout

/** Stable identifier for a persisted [WorkoutSet]. */
@JvmInline
value class WorkoutSetId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "WorkoutSetId must not be blank" }
    }
}
