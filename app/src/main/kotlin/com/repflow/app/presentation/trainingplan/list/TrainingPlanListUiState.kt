package com.repflow.app.presentation.trainingplan.list

import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stable UI state for the training plan list screen. Mirrors
 * [com.repflow.app.presentation.exercise.list.ExerciseListUiState]'s
 * active/archived filter and snackbar-message-queue shape (Milestone 8,
 * CP12) - plans still have no search field, so [filter] is the only
 * criterion.
 */
data class TrainingPlanListUiState(
    val filter: TrainingPlanStatusFilter = TrainingPlanStatusFilter.ACTIVE,
    val content: TrainingPlanListContent = TrainingPlanListContent.Loading,
    val messages: List<TrainingPlanListMessage> = emptyList(),
)

/**
 * Snackbar-worthy events raised by the list screen, mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListMessage].
 */
sealed interface TrainingPlanListMessage {
    val id: Long

    data class Archived(
        override val id: Long,
        val planId: TrainingPlanId,
    ) : TrainingPlanListMessage

    data class OperationFailed(
        override val id: Long,
    ) : TrainingPlanListMessage
}

sealed interface TrainingPlanListContent {
    data object Loading : TrainingPlanListContent

    data class Content(
        val items: List<TrainingPlanListItem>,
    ) : TrainingPlanListContent

    data class Empty(
        val reason: TrainingPlanListEmptyReason,
    ) : TrainingPlanListContent

    data class ObservationFailed(
        val reason: TrainingPlanListFailureReason,
    ) : TrainingPlanListContent
}

enum class TrainingPlanListEmptyReason {
    NO_PLANS,
    NO_ARCHIVED,
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
