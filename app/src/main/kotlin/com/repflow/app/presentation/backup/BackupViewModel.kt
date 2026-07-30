package com.repflow.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.domain.common.DomainResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the backup/restore/CSV-export screen state. The Route composable
 * hosts the SAF ([androidx.activity.result.contract.ActivityResultContracts])
 * launchers and passes already-opened text content in/out - this ViewModel
 * never touches `Uri`, `ContentResolver` or any Android I/O type directly.
 */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val exportBackup: ExportBackup,
        private val restoreBackup: RestoreBackup,
        private val exportWorkoutHistoryCsv: ExportWorkoutHistoryCsv,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BackupUiState())
        val uiState = _uiState.asStateFlow()

        /**
         * Builds the backup JSON text; [onReady] is called with it so the
         * route can write it via SAF. Stays busy, and reports no status yet -
         * the export isn't done until the SAF write actually completes (see
         * [onExportWriteSucceeded]/[onExportWriteCancelled]/[onExportWriteFailed]),
         * not merely once the text has been generated in memory (Milestone 8,
         * CP14: the previous "succeeded" message here fired even if the user
         * went on to cancel the file picker).
         */
        fun onExportBackupRequested(onReady: (String) -> Unit) {
            _uiState.update { it.copy(isBusy = true) }
            viewModelScope.launch {
                val json = exportBackup()
                onReady(json)
            }
        }

        /** Builds the history CSV text; [onReady] is called with it so the route can write it via SAF. Same deferred-status shape as [onExportBackupRequested]. */
        fun onCsvExportRequested(onReady: (String) -> Unit) {
            _uiState.update { it.copy(isBusy = true) }
            viewModelScope.launch {
                val csv = exportWorkoutHistoryCsv()
                onReady(csv)
            }
        }

        /** The route calls this once the SAF write has actually completed successfully. */
        fun onExportWriteSucceeded(kind: BackupExportKind) {
            val message =
                when (kind) {
                    BackupExportKind.BACKUP -> BackupStatusMessage.ExportSucceeded
                    BackupExportKind.CSV -> BackupStatusMessage.CsvExportSucceeded
                }
            _uiState.update { it.copy(isBusy = false, statusMessage = message) }
        }

        /** The user dismissed the SAF picker without choosing a destination - not an error, just clears busy silently. */
        fun onExportWriteCancelled() {
            _uiState.update { it.copy(isBusy = false) }
        }

        /** The SAF picker returned a destination but writing to it failed (e.g. an I/O error). */
        fun onExportWriteFailed() {
            _uiState.update { it.copy(isBusy = false, statusMessage = BackupStatusMessage.OperationFailed) }
        }

        /** The route reads the picked file's text and calls this to ask for restore confirmation. */
        fun onRestoreFilePicked(json: String) {
            _uiState.update { it.copy(pendingRestoreJson = json) }
        }

        /**
         * The route couldn't read the picked restore file - a revoked/unreadable
         * `Uri`, a null stream, or an I/O error (Milestone 8,
         * implementation-review finding #4). No local data is touched, since
         * [onRestoreFilePicked] (the only path that stages a restore) was
         * never reached.
         */
        fun onRestoreFileReadFailed() {
            _uiState.update { it.copy(statusMessage = BackupStatusMessage.OperationFailed) }
        }

        fun onRestoreCancelled() {
            _uiState.update { it.copy(pendingRestoreJson = null) }
        }

        fun onRestoreConfirmed() {
            val json = _uiState.value.pendingRestoreJson ?: return
            _uiState.update { it.copy(isBusy = true, pendingRestoreJson = null) }
            viewModelScope.launch {
                when (val result = restoreBackup(json)) {
                    is DomainResult.Success -> {
                        _uiState.update { it.copy(isBusy = false, statusMessage = BackupStatusMessage.RestoreSucceeded) }
                    }

                    is DomainResult.Failure -> {
                        val message =
                            when (result.error) {
                                is BackupRestoreError.InvalidBackup -> BackupStatusMessage.InvalidBackup
                                BackupRestoreError.Unavailable -> BackupStatusMessage.OperationFailed
                            }
                        _uiState.update { it.copy(isBusy = false, statusMessage = message) }
                    }
                }
            }
        }

        fun onStatusMessageShown() {
            _uiState.update { it.copy(statusMessage = null) }
        }
    }
