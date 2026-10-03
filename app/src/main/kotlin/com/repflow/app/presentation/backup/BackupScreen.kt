package com.repflow.app.presentation.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.exercise.list.ExerciseListSnackbar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The dedicated Backup screen (`5d`, replaced by turn 8's `8c`;
 * remediation-1-remediation-1 CP8), reached from Settings' `Backup and
 * restore`: the hero (`Last backup <when>` over the date and time, or `No
 * backup yet` before the first), `Export backup now`, `Export for other tools`
 * with `Workout history as CSV`, and the `Replaces everything` card with
 * `Restore from a backup`.
 *
 * Restore still goes through the system file picker and the existing
 * destructive confirmation ([BackupRestoreConfirmDialog]) - the only restore
 * path. Registered deviations from the design (Q5): no recent-files list, no
 * `Share`, no safety-snapshot row, no file metadata, no `Undo` toast and no
 * restore file sheet with a preview; a successful restore keeps today's
 * `Backup restored` snackbar.
 */
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    actions: BackupScreenActions,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var exportSaved by rememberSaveable { mutableStateOf(false) }
    BackupMessages(
        message = uiState.statusMessage,
        snackbarHostState = snackbarHostState,
        onExportSaved = { exportSaved = true },
        onShown = actions.onStatusShown,
    )

    if (uiState.pendingRestoreJson != null) {
        BackupRestoreConfirmDialog(onConfirm = actions.onRestoreConfirmed, onCancel = actions.onRestoreCancelled)
    }

    RepFlowScreenScaffold(
        title = stringResource(R.string.backup_title),
        onBack = actions.onBack,
        backContentDescription = stringResource(R.string.backup_back_content_description),
        snackbarHost = { SnackbarHost(snackbarHostState) { ExerciseListSnackbar(it) } },
        modifier = modifier,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp, bottom = 20.dp),
        ) {
            BackupHero(
                uiState = uiState,
                zone = zone,
                exportSaved = exportSaved,
                onExportBackup = {
                    exportSaved = false
                    actions.onExportBackup()
                },
            )
            RepFlowSectionLabel(
                text = stringResource(R.string.backup_section_other_tools),
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            CsvRow(enabled = !uiState.isBusy, onClick = actions.onExportCsv)
            RestoreCard(enabled = !uiState.isBusy, onRestoreClick = actions.onRestoreBackup)
        }
    }
}

/** Shows the export/restore outcome: an export's success is the hero's own `Saved`, everything else a snackbar. */
@Composable
private fun BackupMessages(
    message: BackupStatusMessage?,
    snackbarHostState: SnackbarHostState,
    onExportSaved: () -> Unit,
    onShown: () -> Unit,
) {
    val restoreSucceeded = stringResource(R.string.backup_message_restore_succeeded)
    val csvSucceeded = stringResource(R.string.backup_message_csv_succeeded)
    val invalidBackup = stringResource(R.string.backup_message_invalid_backup)
    val operationFailed = stringResource(R.string.backup_message_operation_failed)
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text =
            when (current) {
                BackupStatusMessage.ExportSucceeded -> null
                BackupStatusMessage.RestoreSucceeded -> restoreSucceeded
                BackupStatusMessage.CsvExportSucceeded -> csvSucceeded
                BackupStatusMessage.InvalidBackup -> invalidBackup
                BackupStatusMessage.OperationFailed -> operationFailed
            }
        // Shown first, consumed after: consuming clears the key this effect is
        // keyed on, and the restart would cancel a snackbar still on screen.
        if (text == null) onExportSaved() else snackbarHostState.showSnackbar(text)
        onShown()
    }
}

@Composable
private fun BackupHero(
    uiState: BackupUiState,
    zone: ZoneId,
    exportSaved: Boolean,
    onExportBackup: () -> Unit,
) {
    val lastBackupAt = uiState.lastBackupAt
    RepFlowCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(HeroPadding)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
            Icon(
                painter = painterResource(if (lastBackupAt != null) RepFlowIcons.shieldCheckFill else RepFlowIcons.shieldWarning),
                contentDescription = null,
                tint = if (lastBackupAt != null) RepFlowColor.accent300 else repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.size(HeroIconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                if (uiState.isLastBackupLoaded) {
                    HeroText(lastBackupAt, uiState.now, zone)
                }
            }
            if (exportSaved) SavedMark()
        }
        Spacer(Modifier.height(14.dp))
        RepFlowPrimaryButton(
            text = stringResource(if (lastBackupAt != null) R.string.backup_export_action else R.string.backup_export_first_action),
            onClick = onExportBackup,
            enabled = !uiState.isBusy,
            leadingIcon = RepFlowIcons.downloadSimple,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.backup_export_note),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = NoteFontSize, lineHeight = NoteLineHeight),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun HeroText(
    lastBackupAt: Instant?,
    now: Instant,
    zone: ZoneId,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    if (lastBackupAt == null) {
        Text(text = stringResource(R.string.backup_none_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.backup_none_meta),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MetaFontSize),
            color = secondary,
            modifier = Modifier.padding(top = 2.dp),
        )
        return
    }
    val title = lastBackupTitle(lastBackupLabel(lastBackupAt, now, zone))
    val local = lastBackupAt.atZone(zone)
    Text(text = title, style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(R.string.backup_last_date_time, local.format(DateFormatter), local.format(TimeFormatter)),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = MetaFontSize),
        color = secondary,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** `Workout history as CSV`: a 52dp hairline row, table glyph, caret. */
@Composable
private fun CsvRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(BorderStroke(1.dp, RepFlowColor.hairline), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(painter = painterResource(RepFlowIcons.table), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(R.string.backup_csv_export_action),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(RepFlowIcons.caretRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.trailingCaretAlpha),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The `Replaces everything` card: the destructive ring, what restoring does, and `Restore from a backup`. */
@Composable
private fun RestoreCard(
    enabled: Boolean,
    onRestoreClick: () -> Unit,
) {
    val destructive = MaterialTheme.colorScheme.error
    Column(
        modifier =
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .border(BorderStroke(1.dp, destructive.copy(alpha = RESTORE_CARD_RING_ALPHA)), RoundedCornerShape(12.dp))
                .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.backup_restore_note),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = NoteLineHeight),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
        Spacer(Modifier.height(RepFlowSpacing.gapLg))
        OutlinedButton(
            onClick = onRestoreClick,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, destructive.copy(alpha = RESTORE_BUTTON_RING_ALPHA)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = destructive),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(painter = painterResource(RepFlowIcons.uploadSimple), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(RepFlowSpacing.gapSm))
            Text(
                text = stringResource(R.string.backup_restore_action),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
            )
        }
    }
}

/** `Saved` beside the hero once an export is written, announced politely when it appears. */
@Composable
private fun SavedMark() {
    Row(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.check),
            contentDescription = null,
            tint = RepFlowColor.accent300,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(R.string.backup_export_saved),
            style = MaterialTheme.typography.bodySmall,
            color = RepFlowColor.accent300,
        )
    }
}

private val HeroPadding = 16.dp
private val HeroIconSize = 26.dp
private val MetaFontSize = 12.5.sp
private val NoteFontSize = 12.sp
private val NoteLineHeight = 18.sp
private const val RESTORE_CARD_RING_ALPHA = 0.35f
private const val RESTORE_BUTTON_RING_ALPHA = 0.7f
private val DateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
