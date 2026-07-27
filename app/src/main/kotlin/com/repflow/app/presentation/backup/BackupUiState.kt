package com.repflow.app.presentation.backup

/** Backup screen state: transient status messages for each of the three actions. */
data class BackupUiState(
    val isBusy: Boolean = false,
    val statusMessage: BackupStatusMessage? = null,
    val pendingRestoreJson: String? = null,
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
