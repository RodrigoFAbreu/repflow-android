package com.repflow.app.domain.progression

/**
 * Stable identifier for a persisted [ProgressionRecommendation].
 *
 * Produced by an application-owned `IdentifierGenerator`, never by the
 * domain itself, mirroring [com.repflow.app.domain.exercise.ExerciseId].
 */
@JvmInline
value class ProgressionRecommendationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ProgressionRecommendationId must not be blank" }
    }
}
