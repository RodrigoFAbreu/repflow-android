package com.repflow.app.application.exercise

/** Which subset of exercises an observer is interested in. */
enum class ExerciseStatusFilter {
    ACTIVE,
    ARCHIVED,
}

/**
 * Criteria for [ExerciseRepository.observe].
 *
 * [normalizedQuery] is expected to already be normalized (see
 * [com.repflow.app.domain.exercise.ExerciseName]) by the caller (typically
 * [ObserveExercises]) - the repository never re-derives it. An empty string
 * means "no query filter".
 */
data class ExerciseQueryCriteria(
    val status: ExerciseStatusFilter = ExerciseStatusFilter.ACTIVE,
    val normalizedQuery: String = "",
)
