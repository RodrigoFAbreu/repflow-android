package com.repflow.app.presentation.backup

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

    // Holds the text produced by the last export request until the SAF
    // picker returns a destination Uri to write it to.
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    val createBackupDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val text = pendingExportText
            pendingExportText = null
            if (uri != null && text != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray(StandardCharsets.UTF_8))
                }
            }
        }
    val createCsvDocumentLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            val text = pendingExportText
            pendingExportText = null
            if (uri != null && text != null) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(text.toByteArray(StandardCharsets.UTF_8))
                }
            }
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

    val updatedViewModel = rememberUpdatedState(viewModel)

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
