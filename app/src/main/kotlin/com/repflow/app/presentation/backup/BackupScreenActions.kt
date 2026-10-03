package com.repflow.app.presentation.backup

/** The Backup screen's callbacks, grouped so the screen's signature stays readable. */
@Suppress("LongParameterList") // a parameter object: one field per callback
class BackupScreenActions(
    val onBack: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onExportCsv: () -> Unit,
    val onRestoreConfirmed: () -> Unit,
    val onRestoreCancelled: () -> Unit,
    val onStatusShown: () -> Unit,
)
