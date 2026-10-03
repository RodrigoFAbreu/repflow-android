package com.repflow.app.presentation.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful Backup route (`5d`): owns [BackupViewModel] with its SAF launchers
 * ([rememberBackupFileActions]) and delegates rendering to [BackupScreen].
 */
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileActions = rememberBackupFileActions(viewModel)
    BackupScreen(
        uiState = uiState,
        actions =
            BackupScreenActions(
                onBack = onBack,
                onExportBackup = fileActions.onExportBackup,
                onRestoreBackup = fileActions.onRestoreBackup,
                onExportCsv = fileActions.onExportCsv,
                onRestoreConfirmed = viewModel::onRestoreConfirmed,
                onRestoreCancelled = viewModel::onRestoreCancelled,
                onStatusShown = viewModel::onStatusMessageShown,
            ),
    )
}
