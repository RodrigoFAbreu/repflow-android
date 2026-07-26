package com.repflow.app.domain.trainingplan

/**
 * Stable identifier for a persisted [TrainingPlan].
 *
 * Produced by an application-owned `IdentifierGenerator`, never by the
 * domain itself (see [com.repflow.app.domain.exercise.ExerciseId] for the
 * same rationale).
 */
@JvmInline
value class TrainingPlanId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "TrainingPlanId must not be blank" }
    }
}
