package com.repflow.app.presentation.settings

/** Settings' callbacks, grouped so the screen's signature stays readable. */
@Suppress("LongParameterList") // a parameter object: one field per callback
class SettingsActions(
    val onBack: () -> Unit,
    val onLibraryClick: () -> Unit,
    val onToggle: (SettingToggle, Boolean) -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onExportCsv: () -> Unit,
    val onRestoreConfirmed: () -> Unit,
    val onRestoreCancelled: () -> Unit,
    val onBackupStatusShown: () -> Unit,
    val onEraseAllDataConfirmed: () -> Unit,
    val onSettingsMessageShown: () -> Unit,
)
