package com.repflow.app.presentation.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

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
 * The existing destructive confirmation in front of a restore: the safe
 * action and the destructive one, nothing replaced until the latter.
 */
@Composable
fun BackupRestoreConfirmDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.backup_restore_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.backup_restore_cancel_action))
            }
        },
    )
}

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
