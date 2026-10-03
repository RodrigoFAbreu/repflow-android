package com.repflow.app.presentation.backup

import java.time.Instant

/**
 * The Backup screen's state: busy, a transient status message, a restore
 * awaiting confirmation, and the hero's `Last backup` - [lastBackupAt] is when
 * a backup export last succeeded on this device (`null`: none yet, once
 * [isLastBackupLoaded]), read against [now].
 */
data class BackupUiState(
    val isBusy: Boolean = false,
    val statusMessage: BackupStatusMessage? = null,
    val pendingRestoreJson: String? = null,
    val lastBackupAt: Instant? = null,
    val isLastBackupLoaded: Boolean = false,
    val now: Instant = Instant.EPOCH,
)

sealed interface BackupStatusMessage {
    data object ExportSucceeded : BackupStatusMessage

    data object RestoreSucceeded : BackupStatusMessage

    data object CsvExportSucceeded : BackupStatusMessage

    data object InvalidBackup : BackupStatusMessage

    data object OperationFailed : BackupStatusMessage
}

/**
 * Which of the two SAF `CreateDocument` exports a write outcome belongs to
 * (Milestone 8, CP14) - [BackupViewModel.onExportWriteSucceeded] needs this
 * to know which of [BackupStatusMessage.ExportSucceeded] /
 * [BackupStatusMessage.CsvExportSucceeded] to show.
 */
enum class BackupExportKind {
    BACKUP,
    CSV,
}
