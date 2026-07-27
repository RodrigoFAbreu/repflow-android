package com.repflow.app.presentation.recovery

import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry

/**
 * UI state for the read-only recovery/futsal history screen (Milestone 8,
 * CP4) - mirrors [com.repflow.app.presentation.history.HistoryUiState]
 * holding domain models directly, since this is a read-only list view with
 * no separate DTO layer.
 */
data class RecoveryHistoryUiState(
    val isLoading: Boolean = true,
    val recoveryEntries: List<RecoveryEntry> = emptyList(),
    val futsalSessions: List<FutsalSession> = emptyList(),
)
