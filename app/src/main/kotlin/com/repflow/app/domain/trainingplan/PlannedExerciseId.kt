package com.repflow.app.domain.trainingplan

/** Stable identifier for a [PlannedExercise] row inside a [TrainingPlanVersion]. */
@JvmInline
value class PlannedExerciseId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "PlannedExerciseId must not be blank" }
    }
}
