package com.repflow.app.presentation.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.common.Clock
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.presentation.workout.RestNotificationCanceller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Owns the backup/restore/CSV-export state, for the dedicated Backup screen
 * (`5d`, remediation-1-remediation-1 CP8; it was Settings' Data group from
 * remediation-1 CP14). It also tracks when the last **backup** export
 * succeeded - `last_backup_at`, a device setting kept out of the backup file -
 * and writes it after a successful backup export, never a CSV one (a "Last
 * backup" after a CSV export would mislead the user about data safety).
 * [rememberBackupFileActions] hosts the SAF
 * ([androidx.activity.result.contract.ActivityResultContracts]) launchers and
 * passes already-opened text content in/out - this ViewModel never touches
 * `Uri`, `ContentResolver` or any Android I/O type directly.
 */
@HiltViewModel
class BackupViewModel
    @Inject
    constructor(
        private val exportBackup: ExportBackup,
        private val restoreBackup: RestoreBackup,
        private val exportWorkoutHistoryCsv: ExportWorkoutHistoryCsv,
        private val restNotificationCanceller: RestNotificationCanceller,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(BackupUiState(now = clock.now()))
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository
                    .observe()
                    .catch { failure ->
                        // A settings read failure must not crash the screen: the hero falls back to
                        // `No backup yet`, and export and restore still work.
                        if (failure is CancellationException) throw failure
                        _uiState.update { it.copy(isLastBackupLoaded = true) }
                    }.collect { settings ->
                        _uiState.update {
                            it.copy(lastBackupAt = settings.lastBackupAt, isLastBackupLoaded = true, now = clock.now())
                        }
                    }
            }
        }

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
            if (kind == BackupExportKind.BACKUP) {
                // A failed write is dropped on purpose: the file is already saved, so the
                // export does not fail - the hero keeps showing the previous time (or
                // `No backup yet`).
                // NonCancellable: leaving the Backup route clears this ViewModel and cancels
                // viewModelScope, which must not drop a stamp for a file that is already saved.
                // The write is one short Room update, so it is not a leak.
                // UNDISPATCHED: the body must start before any cancellation can be observed, so a
                // scope cancelled before a queued dispatch still enters the NonCancellable block.
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    withContext(NonCancellable) { settingsRepository.update { it.copy(lastBackupAt = clock.now()) } }
                }
            }
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
                        restNotificationCanceller.cancel()
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
