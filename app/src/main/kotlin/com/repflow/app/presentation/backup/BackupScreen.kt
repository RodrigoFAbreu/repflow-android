package com.repflow.app.presentation.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R

/** Stateless backup screen: "Export backup", "Restore backup" and "Export history as CSV" actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    onExportBackupClick: () -> Unit,
    onRestoreBackupClick: () -> Unit,
    onCsvExportClick: () -> Unit,
    onRestoreConfirmed: () -> Unit,
    onRestoreCancelled: () -> Unit,
    onStatusMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val exportSucceededText = stringResource(R.string.backup_message_export_succeeded)
    val restoreSucceededText = stringResource(R.string.backup_message_restore_succeeded)
    val csvSucceededText = stringResource(R.string.backup_message_csv_succeeded)
    val invalidBackupText = stringResource(R.string.backup_message_invalid_backup)
    val operationFailedText = stringResource(R.string.backup_message_operation_failed)

    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage ?: return@LaunchedEffect
        val text =
            when (message) {
                BackupStatusMessage.ExportSucceeded -> exportSucceededText
                BackupStatusMessage.RestoreSucceeded -> restoreSucceededText
                BackupStatusMessage.CsvExportSucceeded -> csvSucceededText
                BackupStatusMessage.InvalidBackup -> invalidBackupText
                BackupStatusMessage.OperationFailed -> operationFailedText
            }
        snackbarHostState.showSnackbar(text)
        onStatusMessageShown()
    }

    if (uiState.pendingRestoreJson != null) {
        AlertDialog(
            onDismissRequest = onRestoreCancelled,
            title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
            text = { Text(stringResource(R.string.backup_restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = onRestoreConfirmed) {
                    Text(stringResource(R.string.backup_restore_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onRestoreCancelled) {
                    Text(stringResource(R.string.backup_restore_cancel_action))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.backup_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator()
            }
            Button(onClick = onExportBackupClick, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isBusy) {
                Text(stringResource(R.string.backup_export_action))
            }
            Button(onClick = onRestoreBackupClick, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isBusy) {
                Text(stringResource(R.string.backup_restore_action))
            }
            Button(onClick = onCsvExportClick, modifier = Modifier.fillMaxWidth(), enabled = !uiState.isBusy) {
                Text(stringResource(R.string.backup_csv_export_action))
            }
        }
    }
}
