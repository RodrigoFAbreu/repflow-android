package com.repflow.app.presentation.recovery

import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import java.time.LocalDate

/**
 * UI state for the read-only recovery/futsal history screen (Milestone 8,
 * CP4) - mirrors [com.repflow.app.presentation.history.HistoryUiState]
 * holding domain models directly, since this is a read-only list view with
 * no separate DTO layer.
 *
 * [today] is the injected clock's date at load (remediation-1 CP13): `3d`'s
 * fourteen-day chart ends on it, and the newest entry reads `Today`.
 */
data class RecoveryHistoryUiState(
    val isLoading: Boolean = true,
    val today: LocalDate? = null,
    val recoveryEntries: List<RecoveryEntry> = emptyList(),
    val futsalSessions: List<FutsalSession> = emptyList(),
)
