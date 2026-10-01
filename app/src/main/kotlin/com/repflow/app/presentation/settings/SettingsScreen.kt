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
 * Settings (remediation-1 CP14, artboard `4a`'s `nTabSettings`,
 * `RepFlow.dc.html:1052-1111`): CP3's sub-screen frame, then the groups as the
 * design groups them -
 *
 * - **Library** - `Exercise library`, the library's inward path outside a
 *   workout (register `D25`; `4a` has no such row);
 * - **Rest timer** - auto-start, vibrate and notification switches;
 * - **During a workout** - keep screen awake and confirm before finishing;
 * - **Data** - `Export a backup` (`Saved` once the file is written),
 *   `Restore from a file` (the system picker, then the existing destructive
 *   confirmation) and `Workout history as CSV`, then the `Irreversible` card
 *   over `Erase all data`, behind a typed confirmation;
 * - the footer.
 *
 * `4a`'s `Units` group is not built (`D5`: the domain stores kilograms only).
 * Every switch renders from [SettingsUiState.settings] - what is stored - and
 * waits, disabled, until it has loaded. The whole row is the switch's tap
 * target and it is announced as a switch with its state.
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
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(top = 4.dp, bottom = 20.dp),
        ) {
            SectionLabel(R.string.settings_section_library, first = true)
            SettingsActionRow(
                icon = RepFlowIcons.books,
                title = stringResource(R.string.settings_library_row),
                meta = stringResource(R.string.settings_library_meta),
                onClick = actions.onLibraryClick,
            )
            SwitchGroups(uiState.settings, actions.onToggle)
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
        if (text == null) onExportSaved()
        actions.onBackupStatusShown()
        if (text != null) snackbarHostState.showSnackbar(text)
    }
    LaunchedEffect(uiState.message) {
        val message = uiState.message ?: return@LaunchedEffect
        val text =
            when (message) {
                SettingsMessage.SAVE_FAILED -> saveFailed
                SettingsMessage.ERASED -> erased
                SettingsMessage.ERASE_FAILED -> eraseFailed
            }
        actions.onSettingsMessageShown()
        snackbarHostState.showSnackbar(text)
    }
}

@Composable
private fun SwitchGroups(
    settings: AppSettings?,
    onToggle: (SettingToggle, Boolean) -> Unit,
) {
    SectionLabel(R.string.settings_section_rest_timer)
    SettingsSwitchRow(
        R.string.settings_rest_auto_start,
        R.string.settings_rest_auto_start_meta,
        SettingToggle.REST_TIMER_AUTO_START,
        settings,
        onToggle,
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
