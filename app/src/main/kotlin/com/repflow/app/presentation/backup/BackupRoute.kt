package com.repflow.app.presentation.backup

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Stateful route composable: owns the ViewModel and the SAF
 * (Storage Access Framework) launchers for the three backup actions. Only
 * this Route touches `Uri`/`ContentResolver` - the ViewModel and Screen work
 * with plain text (see [BackupViewModel]'s doc comment).
 */
@Composable
fun BackupRoute(viewModel: BackupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val updatedViewModel = rememberUpdatedState(viewModel)

    // Holds the text produced by the last export request until the SAF
    // picker returns a destination Uri to write it to.
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createBackupDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            writeExportOrReportOutcome(context, pendingExportText, uri, BackupExportKind.BACKUP, updatedViewModel.value)
            pendingExportText = null
        }
    val createCsvDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            writeExportOrReportOutcome(context, pendingExportText, uri, BackupExportKind.CSV, updatedViewModel.value)
            pendingExportText = null
        }
    val openBackupDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val text =
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).readText()
                }
            if (text != null) {
                viewModel.onRestoreFilePicked(text)
            }
        }

    BackupScreen(
        uiState = uiState,
        onExportBackupClick = {
            updatedViewModel.value.onExportBackupRequested { json ->
                pendingExportText = json
                createBackupDocumentLauncher.launch(BACKUP_FILE_NAME)
            }
        },
        onRestoreBackupClick = { openBackupDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
        onCsvExportClick = {
            updatedViewModel.value.onCsvExportRequested { csv ->
                pendingExportText = csv
                createCsvDocumentLauncher.launch(CSV_FILE_NAME)
            }
        },
        onRestoreConfirmed = viewModel::onRestoreConfirmed,
        onRestoreCancelled = viewModel::onRestoreCancelled,
        onStatusMessageShown = viewModel::onStatusMessageShown,
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
