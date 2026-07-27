package com.repflow.app.application.backup

import com.repflow.app.domain.backup.BackupValidationError

/** Failures surfaced while restoring a backup, spanning parsing/validation and persistence. */
sealed interface BackupRestoreError {
    data class InvalidBackup(
        val reason: BackupValidationError,
    ) : BackupRestoreError

    data object Unavailable : BackupRestoreError
}
