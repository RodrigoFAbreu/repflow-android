package com.repflow.app.domain.workout

/** Stable identifier for a persisted [WorkoutSession]. */
@JvmInline
value class WorkoutSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "WorkoutSessionId must not be blank" }
    }
}
