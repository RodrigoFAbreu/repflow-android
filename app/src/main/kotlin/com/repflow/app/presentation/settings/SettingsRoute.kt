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

/**
 * Stateful Settings route (remediation-1 CP14): owns [SettingsViewModel] for
 * the switches, the choices and `Erase all data`. Backup and restore live on
 * their own screen again (`5d`, remediation-1-remediation-1 CP8), reached by
 * [onBackupClick].
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onLibraryClick: () -> Unit,
    onArchivedClick: () -> Unit,
    onBackupClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionName = remember(context) { appVersionName(context) }

    SettingsScreen(
        uiState = uiState,
        versionName = versionName,
        actions =
            SettingsActions(
                onBack = onBack,
                onLibraryClick = onLibraryClick,
                onArchivedClick = onArchivedClick,
                onBackupClick = onBackupClick,
                onToggle = settingsViewModel::onToggle,
                onThemeSelected = settingsViewModel::onThemeSelected,
                onDefaultRestSelected = settingsViewModel::onDefaultRestSelected,
                onExtraSetFieldsSelected = settingsViewModel::onExtraSetFieldsSelected,
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
