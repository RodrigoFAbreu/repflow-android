package com.repflow.app.presentation.history

import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId

/**
 * History screen state: the completed-session list plus an optional
 * read-only detail selection.
 *
 * `messages` is a FIFO queue of [HistoryMessage]; each has a stable, unique,
 * monotonic id and is consumed via `onMessageShown(id)`, mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListUiState]'s
 * snackbar-hardening pattern (Milestone 8, CP2).
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val selectedSessionId: WorkoutSessionId? = null,
    val messages: List<HistoryMessage> = emptyList(),
) {
    val selectedSession: WorkoutSession?
        get() = sessions.find { it.id == selectedSessionId }
}

/** Snackbar-worthy events raised by the history screen. */
sealed interface HistoryMessage {
    val id: Long

    data class Invalidated(
        override val id: Long,
    ) : HistoryMessage

    data class OperationFailed(
        override val id: Long,
    ) : HistoryMessage
}
