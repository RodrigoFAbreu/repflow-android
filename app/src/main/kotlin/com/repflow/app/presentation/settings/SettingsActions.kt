package com.repflow.app.presentation.settings

import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.ThemeMode

/** Settings' callbacks, grouped so the screen's signature stays readable. */
@Suppress("LongParameterList") // a parameter object: one field per callback
class SettingsActions(
    val onBack: () -> Unit,
    val onLibraryClick: () -> Unit,
    val onArchivedClick: () -> Unit,
    val onBackupClick: () -> Unit,
    val onToggle: (SettingToggle, Boolean) -> Unit,
    val onThemeSelected: (ThemeMode) -> Unit,
    val onDefaultRestSelected: (Int) -> Unit,
    val onExtraSetFieldsSelected: (ExtraSetFields) -> Unit,
    val onEraseAllDataConfirmed: () -> Unit,
    val onSettingsMessageShown: () -> Unit,
)
