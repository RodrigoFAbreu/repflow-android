package com.repflow.app.presentation.exercise.list

import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.exercise.ExerciseId

/**
 * Stable UI state for the exercise list screen (see plan.md section H).
 *
 * `messages` is a FIFO queue of [ExerciseListMessage]; each has a stable,
 * unique, monotonic id and is consumed via `onMessageShown(id)`, so a newer
 * message queued while an older snackbar is displaying can never be
 * discarded (D-24-adjacent correction 11).
 */
data class ExerciseListUiState(
    val query: String = "",
    val filter: ExerciseStatusFilter = ExerciseStatusFilter.ACTIVE,
    val content: ExerciseListContent = ExerciseListContent.Loading,
    val messages: List<ExerciseListMessage> = emptyList(),
)

/**
 * Snackbar-worthy events raised by the list screen. `Archived` carries the
 * archived exercise's id so the snackbar's Undo action can call
 * `RestoreExercise` directly - offline-first, nothing is held in memory
 * pending a snackbar timeout (plan.md section B, "Archive / restore").
 */
sealed interface ExerciseListMessage {
    val id: Long

    data class Archived(
        override val id: Long,
        val exerciseId: ExerciseId,
    ) : ExerciseListMessage

    data class OperationFailed(
        override val id: Long,
    ) : ExerciseListMessage
}

sealed interface ExerciseListContent {
    data object Loading : ExerciseListContent

    data class Content(
        val items: List<ExerciseListItem>,
    ) : ExerciseListContent

    data class Empty(
        val reason: ExerciseListEmptyReason,
    ) : ExerciseListContent

    data class ObservationFailed(
        val reason: ExerciseListFailureReason,
    ) : ExerciseListContent
}

enum class ExerciseListEmptyReason {
    NO_EXERCISES,
    NO_SEARCH_RESULTS,
    NO_ARCHIVED,
}

/**
 * The specific persistence-layer cause is deliberately not surfaced to the
 * user (D-24 only requires a visible, retryable failure state) - there is
 * currently one reason, kept as an enum rather than a plain boolean so a
 * future distinction does not require changing [ExerciseListContent]'s shape.
 */
enum class ExerciseListFailureReason {
    UNKNOWN,
}
