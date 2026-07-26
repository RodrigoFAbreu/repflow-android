package com.repflow.app.presentation.trainingplan.list

/**
 * Stable UI state for the training plan list screen. Simpler than
 * [com.repflow.app.presentation.exercise.list.ExerciseListUiState]: there is
 * no search or archived/active filter for plans in M2 (no archive concept
 * exists yet, see the reference doc's deferred-scope table), so [content]
 * is the only observed piece of state.
 */
data class TrainingPlanListUiState(
    val content: TrainingPlanListContent = TrainingPlanListContent.Loading,
)

sealed interface TrainingPlanListContent {
    data object Loading : TrainingPlanListContent

    data class Content(
        val items: List<TrainingPlanListItem>,
    ) : TrainingPlanListContent

    data object Empty : TrainingPlanListContent

    data class ObservationFailed(
        val reason: TrainingPlanListFailureReason,
    ) : TrainingPlanListContent
}

/**
 * The specific persistence-layer cause is deliberately not surfaced to the
 * user (mirrors `ExerciseListFailureReason`'s rationale) - kept as an enum
 * rather than a plain boolean so a future distinction does not require
 * changing [TrainingPlanListContent]'s shape.
 */
enum class TrainingPlanListFailureReason {
    UNKNOWN,
}
