package com.repflow.app.presentation.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId

/**
 * The three backup actions, wired to their SAF (Storage Access Framework)
 * pickers: export a backup, restore from a file, export history as CSV.
 *
 * They are the Backup screen's buttons (`5d`, remediation-1-remediation-1
 * CP8; rows in Settings' `Data` group from remediation-1 CP14 until then);
 * the wiring is the original Backup route's, unchanged. Only this file touches `Uri`/`ContentResolver` -
 * [BackupViewModel] works with plain text.
 */
class BackupFileActions(
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onExportCsv: () -> Unit,
)

/** Registers the three SAF launchers against [viewModel] and returns the actions that open them. */
@Composable
fun rememberBackupFileActions(viewModel: BackupViewModel): BackupFileActions {
    val context = LocalContext.current
    val updatedViewModel by rememberUpdatedState(viewModel)

    // Holds the text produced by the last export request until the SAF
    // picker returns a destination Uri to write it to.
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createBackupDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            writeExportOrReportOutcome(context, pendingExportText, uri, BackupExportKind.BACKUP, updatedViewModel)
            pendingExportText = null
        }
    val createCsvDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            writeExportOrReportOutcome(context, pendingExportText, uri, BackupExportKind.CSV, updatedViewModel)
            pendingExportText = null
        }
    val openBackupDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            readRestoreFileOrReportFailure(context, uri, updatedViewModel)
        }

    return remember(createBackupDocumentLauncher, createCsvDocumentLauncher, openBackupDocumentLauncher) {
        BackupFileActions(
            onExportBackup = {
                updatedViewModel.onExportBackupRequested { json ->
                    pendingExportText = json
                    createBackupDocumentLauncher.launch(BACKUP_FILE_NAME)
                }
            },
            onRestoreBackup = { openBackupDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            onExportCsv = {
                updatedViewModel.onCsvExportRequested { csv ->
                    pendingExportText = csv
                    createCsvDocumentLauncher.launch(CSV_FILE_NAME)
                }
            },
        )
    }
}

/**
 * The destructive confirmation in front of a restore (`40j`), of a file or, when
 * [pendingRestoreSnapshotAt] is non-null, of the safety copy: the safe action and
 * the destructive one, nothing replaced until the latter. It warns first when
 * [hasRunningWorkout], because a restore deletes that workout.
 *
 * Both buttons share `confirmButton`'s slot, in a layout this code controls, so
 * their order does not depend on how Material wraps its own row.
 */
@Composable
fun BackupRestoreConfirmDialog(
    hasRunningWorkout: Boolean,
    preRestoreSnapshotAt: Instant?,
    pendingRestoreSnapshotAt: Instant?,
    hasTrainingData: Boolean,
    zone: ZoneId,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text =
                    stringResource(
                        if (pendingRestoreSnapshotAt != null) {
                            R.string.backup_safety_copy_confirm_title
                        } else {
                            R.string.backup_restore_confirm_title
                        },
                    ),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 24.sp),
            )
        },
        text = {
            BackupRestoreConfirmText(
                hasRunningWorkout = hasRunningWorkout,
                preRestoreSnapshotAt = preRestoreSnapshotAt,
                pendingRestoreSnapshotAt = pendingRestoreSnapshotAt,
                hasTrainingData = hasTrainingData,
                zone = zone,
            )
        },
        confirmButton = { BackupRestoreConfirmButtons(onConfirm = onConfirm, onCancel = onCancel) },
        textContentColor = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
    )
}

/**
 * `Cancel` on the left, or, at a large font, stacked with the destructive action on top (`40j`). In the row
 * they stay one height if a narrow phone wraps the longer label.
 */
@Composable
private fun BackupRestoreConfirmButtons(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmLabel = stringResource(R.string.backup_restore_confirm_action)
    val cancelLabel = stringResource(R.string.backup_restore_cancel_action)
    if (LocalDensity.current.fontScale > STACK_ACTIONS_FONT_SCALE) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            BackupOutlinedButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), destructive = true)
            BackupOutlinedButton(cancelLabel, onCancel, Modifier.fillMaxWidth())
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            BackupOutlinedButton(cancelLabel, onCancel, Modifier.weight(1f).fillMaxHeight())
            BackupOutlinedButton(confirmLabel, onConfirm, Modifier.weight(1f).fillMaxHeight(), destructive = true)
        }
    }
}

/**
 * The outlined button `40j` and `40k` draw: at least 48dp, in a neutral colour or the destructive one,
 * and never filled.
 */
@Composable
internal fun BackupOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (destructive) colorScheme.error else RepFlowColor.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructive) colorScheme.error else colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The confirmation's text. It scrolls so it never clips at a large font size,
 * and the warning comes first so it shows without scrolling at any size. With
 * no training data no copy is made and none is replaced, so only the first
 * paragraph of each variant shows.
 */
@Composable
fun BackupRestoreConfirmText(
    hasRunningWorkout: Boolean,
    preRestoreSnapshotAt: Instant?,
    pendingRestoreSnapshotAt: Instant?,
    hasTrainingData: Boolean,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp)) {
        Column(
            modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            if (hasRunningWorkout) {
                Text(stringResource(R.string.backup_restore_confirm_running_workout))
            }
            if (pendingRestoreSnapshotAt != null) {
                val takenAt = pendingRestoreSnapshotAt.atZone(zone)
                Text(
                    stringResource(
                        R.string.backup_safety_copy_confirm_body,
                        takenAt.format(DateFormatter),
                        takenAt.format(TimeFormatter),
                    ),
                )
                if (hasTrainingData) Text(stringResource(R.string.backup_safety_copy_confirm_keeps_copy))
            } else {
                Text(stringResource(R.string.backup_restore_confirm_body))
                if (hasTrainingData) {
                    Text(stringResource(R.string.backup_restore_confirm_keeps_copy))
                    if (preRestoreSnapshotAt != null) {
                        val takenAt = preRestoreSnapshotAt.atZone(zone)
                        Text(
                            stringResource(
                                R.string.backup_restore_confirm_replaces_copy,
                                takenAt.format(DateFormatter),
                                takenAt.format(TimeFormatter),
                            ),
                        )
                    }
                }
            }
        }
    }
}

private const val STACK_ACTIONS_FONT_SCALE = 1.1f
private const val BACKUP_FILE_NAME = "repflow-backup.json"
private const val CSV_FILE_NAME = "repflow-history.csv"

/**
 * Writes [text] to [uri] and reports the real outcome back to the ViewModel
 * (Milestone 8, CP14): `uri == null` means the user cancelled the SAF
 * picker - not an error, so no failure message. A null [text] should never
 * happen (the picker only launches after export text is already pending)
 * but is treated as a failure defensively rather than silently doing
 * nothing. An actual write failure (e.g. the destination becoming
 * unavailable mid-write) is caught narrowly - [IOException] is exactly the
 * exception family [java.io.OutputStream.write] and
 * [android.content.ContentResolver.openOutputStream] declare - never a
 * broad `catch (e: Exception)`.
 */
private fun writeExportOrReportOutcome(
    context: Context,
    text: String?,
    uri: Uri?,
    kind: BackupExportKind,
    viewModel: BackupViewModel,
) {
    if (uri == null) {
        viewModel.onExportWriteCancelled()
        return
    }
    if (text == null) {
        viewModel.onExportWriteFailed()
        return
    }
    try {
        val stream =
            context.contentResolver.openOutputStream(uri) ?: run {
                viewModel.onExportWriteFailed()
                return
            }
        stream.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) }
        viewModel.onExportWriteSucceeded(kind)
    } catch (expected: IOException) {
        viewModel.onExportWriteFailed()
    }
}

/**
 * Reads [uri]'s text content and reports the outcome back to the ViewModel
 * (Milestone 8, implementation-review finding #4): a revoked or otherwise
 * unreadable `Uri` throws [SecurityException] or [IOException] from
 * [android.content.ContentResolver.openInputStream] or the subsequent read
 * - both are caught narrowly here, never a broad `catch (e: Exception)` -
 * and a `null` stream (no provider for this `Uri`) is treated the same way.
 * Cancellation (a `null` `Uri`) is handled by the caller before this is
 * ever invoked, so every path here is a real outcome that must be reported.
 */
private fun readRestoreFileOrReportFailure(
    context: Context,
    uri: Uri,
    viewModel: BackupViewModel,
) {
    try {
        val stream =
            context.contentResolver.openInputStream(uri) ?: run {
                viewModel.onRestoreFileReadFailed()
                return
            }
        val text = stream.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() }
        viewModel.onRestoreFilePicked(text)
    } catch (expected: IOException) {
        viewModel.onRestoreFileReadFailed()
    } catch (expected: SecurityException) {
        viewModel.onRestoreFileReadFailed()
    }
}
