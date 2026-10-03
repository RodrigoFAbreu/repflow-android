package com.repflow.app.presentation.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.presentation.backup.BackupViewModel
import com.repflow.app.presentation.backup.rememberBackupFileActions

/**
 * Stateful Settings route (remediation-1 CP14): owns [SettingsViewModel] for
 * the switches and `Erase all data`, and [BackupViewModel] with its SAF
 * launchers for the Data group's backup rows - the actions the Backup screen
 * used to carry before `4a` put them inline here.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onLibraryClick: () -> Unit,
    onArchivedClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()
    val backupActions = rememberBackupFileActions(backupViewModel)
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }

    SettingsScreen(
        uiState = uiState,
        backupState = backupState,
        versionName = versionName,
        actions =
            SettingsActions(
                onBack = onBack,
                onLibraryClick = onLibraryClick,
                onArchivedClick = onArchivedClick,
                onToggle = settingsViewModel::onToggle,
                onThemeSelected = settingsViewModel::onThemeSelected,
                onDefaultRestSelected = settingsViewModel::onDefaultRestSelected,
                onExtraSetFieldsSelected = settingsViewModel::onExtraSetFieldsSelected,
                onExportBackup = backupActions.onExportBackup,
                onRestoreBackup = backupActions.onRestoreBackup,
                onExportCsv = backupActions.onExportCsv,
                onRestoreConfirmed = backupViewModel::onRestoreConfirmed,
                onRestoreCancelled = backupViewModel::onRestoreCancelled,
                onBackupStatusShown = backupViewModel::onStatusMessageShown,
                onEraseAllDataConfirmed = settingsViewModel::onEraseAllDataConfirmed,
                onSettingsMessageShown = settingsViewModel::onMessageShown,
            ),
    )
}

/** This build's `versionName` for the footer, or `null` if the package manager cannot say. */
private fun appVersionName(context: Context): String? =
    try {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        info.versionName
    } catch (expected: PackageManager.NameNotFoundException) {
        null
    }
