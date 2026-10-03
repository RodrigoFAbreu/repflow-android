package com.repflow.app.presentation.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.presentation.backup.BackupRestoreConfirmDialog
import com.repflow.app.presentation.backup.BackupStatusMessage
import com.repflow.app.presentation.backup.BackupUiState
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * Settings, converted to the newer `5c` (remediation-1-remediation-1 CP7), in
 * CP3's sub-screen frame, grouped by when you would change something -
 *
 * - **Library** - `Exercise library`, the library's inward path outside a
 *   workout (register `D25`; `5c` has no such row);
 * - **Units and appearance** - `Theme` as three full-width segments
 *   (`Weight unit` is not built: `D5`, the domain stores kilograms only);
 * - **Rest timer** - auto-start, the `Default rest` row (opens a sheet),
 *   vibrate and notification switches;
 * - **During a workout** - keep screen awake, confirm before finishing and the
 *   `Extra set fields` row (opens a sheet);
 * - **Data** - `Archived exercises and plans` (the Archived screen) and, until
 *   the dedicated Backup screen (`5d`, CP8) replaces them, the three backup
 *   rows (`Saved` once an export is written; restore goes through the system
 *   picker and the existing destructive confirmation), then the `Irreversible`
 *   card over `Erase all data`, behind a typed confirmation;
 * - the footer.
 *
 * Every switch, the theme and the two value rows render from
 * [SettingsUiState.settings] - what is stored - and wait, disabled, until it
 * has loaded. A switch's whole row is its tap target and it is announced as a
 * switch with its state.
 */
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    backupState: BackupUiState,
    versionName: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var exportSaved by rememberSaveable { mutableStateOf(false) }
    var showEraseConfirm by rememberSaveable { mutableStateOf(false) }
    var showRestSheet by rememberSaveable { mutableStateOf(false) }
    var showExtraSheet by rememberSaveable { mutableStateOf(false) }
    SettingsMessages(
        uiState = uiState,
        backupState = backupState,
        snackbarHostState = snackbarHostState,
        onExportSaved = { exportSaved = true },
        actions = actions,
    )

    if (backupState.pendingRestoreJson != null) {
        BackupRestoreConfirmDialog(onConfirm = actions.onRestoreConfirmed, onCancel = actions.onRestoreCancelled)
    }
    if (showEraseConfirm) {
        EraseAllDataDialog(
            onConfirm = {
                showEraseConfirm = false
                actions.onEraseAllDataConfirmed()
            },
            onDismiss = { showEraseConfirm = false },
        )
    }

    uiState.settings?.let { settings ->
        if (showRestSheet) {
            DefaultRestSheet(
                currentSeconds = settings.defaultRestSeconds,
                onSelect = actions.onDefaultRestSelected,
                onDismiss = { showRestSheet = false },
            )
        }
        if (showExtraSheet) {
            ExtraSetFieldsSheet(
                current = settings.extraSetFields,
                onSelect = actions.onExtraSetFieldsSelected,
                onDismiss = { showExtraSheet = false },
            )
        }
    }

    RepFlowScreenScaffold(
        title = stringResource(R.string.settings_title),
        onBack = actions.onBack,
        backContentDescription = stringResource(R.string.settings_back_content_description),
        snackbarHost = { SnackbarHost(snackbarHostState) { SettingsSnackbar(it) } },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 20.dp),
        ) {
            SectionLabel(R.string.settings_section_library, first = true)
            SettingsActionRow(
                icon = RepFlowIcons.books,
                title = stringResource(R.string.settings_library_row),
                meta = stringResource(R.string.settings_library_meta),
                onClick = actions.onLibraryClick,
            )
            SettingsGroups(
                settings = uiState.settings,
                actions = actions,
                onDefaultRestClick = { showRestSheet = true },
                onExtraSetFieldsClick = { showExtraSheet = true },
            )
            DataGroup(
                backupState = backupState,
                exportSaved = exportSaved,
                onExportBackup = {
                    exportSaved = false
                    actions.onExportBackup()
                },
                actions = actions,
            )
            EraseCard(enabled = !uiState.isErasing, onEraseClick = { showEraseConfirm = true })
            Text(
                text =
                    versionName?.let { stringResource(R.string.settings_footer, it) }
                        ?: stringResource(R.string.settings_footer_no_version),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = FooterFontSize),
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            )
        }
    }
}

/**
 * Both message channels: the backup actions' status (an export's success is the
 * row's own `Saved`, not a snackbar - `4a`'s `nBackupDone`) and Settings' own.
 */
@Composable
private fun SettingsMessages(
    uiState: SettingsUiState,
    backupState: BackupUiState,
    snackbarHostState: SnackbarHostState,
    onExportSaved: () -> Unit,
    actions: SettingsActions,
) {
    val restoreSucceeded = stringResource(R.string.backup_message_restore_succeeded)
    val csvSucceeded = stringResource(R.string.backup_message_csv_succeeded)
    val invalidBackup = stringResource(R.string.backup_message_invalid_backup)
    val operationFailed = stringResource(R.string.backup_message_operation_failed)
    val erased = stringResource(R.string.settings_message_erased)
    val eraseFailed = stringResource(R.string.settings_message_erase_failed)
    val saveFailed = stringResource(R.string.settings_message_save_failed)

    LaunchedEffect(backupState.statusMessage) {
        val message = backupState.statusMessage ?: return@LaunchedEffect
        val text =
            when (message) {
                BackupStatusMessage.ExportSucceeded -> null
                BackupStatusMessage.RestoreSucceeded -> restoreSucceeded
                BackupStatusMessage.CsvExportSucceeded -> csvSucceeded
                BackupStatusMessage.InvalidBackup -> invalidBackup
                BackupStatusMessage.OperationFailed -> operationFailed
            }
        // Shown first, consumed after: consuming clears the key this effect is
        // keyed on, and the restart would cancel a snackbar still on screen.
        if (text == null) onExportSaved() else snackbarHostState.showSnackbar(text)
        actions.onBackupStatusShown()
    }
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        val text =
            when (message) {
                SettingsMessage.SAVE_FAILED -> saveFailed
                SettingsMessage.ERASED -> erased
                SettingsMessage.ERASE_FAILED -> eraseFailed
            }
        snackbarHostState.showSnackbar(text)
        actions.onSettingsMessageShown()
    }
}

@Composable
private fun SettingsGroups(
    settings: AppSettings?,
    actions: SettingsActions,
    onDefaultRestClick: () -> Unit,
    onExtraSetFieldsClick: () -> Unit,
) {
    val onToggle = actions.onToggle
    SectionLabel(R.string.settings_section_units_appearance)
    ThemeSegments(selected = settings?.theme, onSelect = actions.onThemeSelected)
    SectionLabel(R.string.settings_section_rest_timer)
    SettingsSwitchRow(
        R.string.settings_rest_auto_start,
        R.string.settings_rest_auto_start_meta,
        SettingToggle.REST_TIMER_AUTO_START,
        settings,
        onToggle,
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_default_rest_row),
        meta = stringResource(R.string.settings_default_rest_row_meta),
        value = settings?.let { formatRest(it.defaultRestSeconds) },
        onClick = onDefaultRestClick,
    )
    SettingsSwitchRow(
        R.string.settings_rest_vibrate,
        R.string.settings_rest_vibrate_meta,
        SettingToggle.REST_TIMER_VIBRATE,
        settings,
        onToggle,
    )
    SettingsSwitchRow(
        R.string.settings_rest_notification,
        R.string.settings_rest_notification_meta,
        SettingToggle.REST_TIMER_NOTIFICATION,
        settings,
        onToggle,
    )
    SectionLabel(R.string.settings_section_workout)
    SettingsSwitchRow(
        R.string.settings_keep_screen_awake,
        R.string.settings_keep_screen_awake_meta,
        SettingToggle.KEEP_SCREEN_AWAKE,
        settings,
        onToggle,
    )
    SettingsSwitchRow(
        R.string.settings_confirm_finish,
        R.string.settings_confirm_finish_meta,
        SettingToggle.CONFIRM_BEFORE_FINISHING,
        settings,
        onToggle,
    )
    SettingsValueRow(
        title = stringResource(R.string.settings_extra_set_fields_row),
        meta = stringResource(R.string.settings_extra_set_fields_row_meta),
        value = settings?.let { stringResource(extraSetFieldsLabelRes(it.extraSetFields)) },
        onClick = onExtraSetFieldsClick,
    )
}

@Composable
private fun DataGroup(
    backupState: BackupUiState,
    exportSaved: Boolean,
    onExportBackup: () -> Unit,
    actions: SettingsActions,
) {
    val enabled = !backupState.isBusy
    SectionLabel(R.string.settings_section_data)
    SettingsActionRow(
        icon = RepFlowIcons.archive,
        title = stringResource(R.string.settings_archived_row),
        meta = "",
        onClick = actions.onArchivedClick,
    )
    SettingsActionRow(
        icon = RepFlowIcons.database,
        title = stringResource(R.string.backup_export_action),
        meta = stringResource(R.string.backup_export_meta),
        onClick = onExportBackup,
        enabled = enabled,
        trailing = if (exportSaved) ({ SavedMark() }) else ({}),
    )
    SettingsActionRow(
        icon = RepFlowIcons.arrowCounterClockwise,
        title = stringResource(R.string.backup_restore_action),
        meta = stringResource(R.string.backup_restore_meta),
        onClick = actions.onRestoreBackup,
        enabled = enabled,
    )
    SettingsActionRow(
        icon = RepFlowIcons.table,
        title = stringResource(R.string.backup_csv_export_action),
        meta = stringResource(R.string.backup_csv_export_meta),
        onClick = actions.onExportCsv,
        enabled = enabled,
    )
}

/** The design's toast card: the surface fill inside a hairline ring, radius 12. */
@Composable
private fun SettingsSnackbar(data: SnackbarData) {
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd)
                .border(1.dp, RepFlowColor.hairline, SnackbarShape),
        shape = SnackbarShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}

private val FooterFontSize = 12.sp
private val SnackbarShape = RoundedCornerShape(12.dp)
