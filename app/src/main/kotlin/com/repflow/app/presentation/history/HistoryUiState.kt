package com.repflow.app.presentation.history

import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId

/** History screen state: the completed-session list plus an optional read-only detail selection. */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val selectedSessionId: WorkoutSessionId? = null,
) {
    val selectedSession: WorkoutSession?
        get() = sessions.find { it.id == selectedSessionId }
}
