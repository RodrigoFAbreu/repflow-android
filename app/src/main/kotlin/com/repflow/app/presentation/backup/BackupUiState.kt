package com.repflow.app.presentation.backup

import java.time.Instant

/**
 * The Backup screen's state: busy, a transient status message, a restore
 * awaiting confirmation, and the hero's `Last backup` - [lastBackupAt] is when
 * a backup export last succeeded on this device (`null`: none yet, once
 * [isLastBackupLoaded]), read against [now]. [hasRunningWorkout] is `true` when a
 * workout is running, or when that isn't known - before the first read, or after a
 * failed one: the restore confirmation warns.
 *
 * [preRestoreSnapshotAt] is when the safety copy on offer was taken (`null`: none, so no card).
 * [pendingRestoreSnapshotAt] is non-null when the restore awaiting confirmation is that copy, and gives
 * its time. [hasTrainingData] is read when the screen opens, each time a restore is staged and after a
 * restore ends, and is `true` when that isn't known: the note and the confirmation never silently drop
 * the copy's wording.
 */
data class BackupUiState(
    val isBusy: Boolean = false,
    val statusMessage: BackupStatusMessage? = null,
    val pendingRestoreJson: String? = null,
    val lastBackupAt: Instant? = null,
    val isLastBackupLoaded: Boolean = false,
    val now: Instant = Instant.EPOCH,
    val hasRunningWorkout: Boolean = true,
    val preRestoreSnapshotAt: Instant? = null,
    val pendingRestoreSnapshotAt: Instant? = null,
    val hasTrainingData: Boolean = true,
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
