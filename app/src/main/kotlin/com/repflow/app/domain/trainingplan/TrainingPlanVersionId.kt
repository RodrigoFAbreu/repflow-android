package com.repflow.app.domain.trainingplan

/** Stable identifier for a persisted [TrainingPlanVersion]. */
@JvmInline
value class TrainingPlanVersionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "TrainingPlanVersionId must not be blank" }
    }
}
