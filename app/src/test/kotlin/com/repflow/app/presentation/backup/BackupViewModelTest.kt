package com.repflow.app.presentation.backup

import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.InMemoryBackupRepository
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.settings.SettingsPersistenceError
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.presentation.workout.RecordingRestNotificationCanceller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val workoutRepository = InMemoryWorkoutRepository()
    private val recoveryRepository = InMemoryRecoveryRepository()
    private val futsalRepository = InMemoryFutsalRepository()
    private val progressionRepository = InMemoryProgressionRecommendationRepository()
    private val backupRepository = InMemoryBackupRepository()

    private val exportBackup =
        ExportBackup(
            exerciseRepository = exerciseRepository,
            trainingPlanRepository = trainingPlanRepository,
            workoutRepository = workoutRepository,
            recoveryRepository = recoveryRepository,
            futsalRepository = futsalRepository,
            progressionRepository = progressionRepository,
            backupRepository = backupRepository,
        )
    private val restoreBackup = RestoreBackup(backupRepository)
    private val exportWorkoutHistoryCsv =
        ExportWorkoutHistoryCsv(workoutRepository, ObserveTrainingPlanVersionLabels(trainingPlanRepository))

    private val restNotificationCanceller = RecordingRestNotificationCanceller()
    private val settingsRepository = InMemorySettingsRepository()
    private val clock = FixedClock(Instant.parse("2026-10-03T12:00:00Z"))
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Built after the Main dispatcher is set: the ViewModel starts observing the settings in `init`.
        viewModel =
            BackupViewModel(exportBackup, restoreBackup, exportWorkoutHistoryCsv, restNotificationCanceller, settingsRepository, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onExportBackupRequested delivers the serialized snapshot and stays busy until the write completes`() =
        runTest {
            var delivered: String? = null

            viewModel.onExportBackupRequested { delivered = it }

            assertNotNull(delivered)
            assertNull(viewModel.uiState.value.statusMessage)
            assertEquals(true, viewModel.uiState.value.isBusy)

            viewModel.onExportWriteSucceeded(BackupExportKind.BACKUP)

            assertEquals(BackupStatusMessage.ExportSucceeded, viewModel.uiState.value.statusMessage)
            assertEquals(false, viewModel.uiState.value.isBusy)
        }

    @Test
    fun `onCsvExportRequested delivers CSV text and stays busy until the write completes`() =
        runTest {
            var delivered: String? = null

            viewModel.onCsvExportRequested { delivered = it }

            assertNotNull(delivered)
            assertTrue(delivered!!.startsWith("session_id,"))
            assertNull(viewModel.uiState.value.statusMessage)
            assertEquals(true, viewModel.uiState.value.isBusy)

            viewModel.onExportWriteSucceeded(BackupExportKind.CSV)

            assertEquals(BackupStatusMessage.CsvExportSucceeded, viewModel.uiState.value.statusMessage)
            assertEquals(false, viewModel.uiState.value.isBusy)
        }

    @Test
    fun `onExportWriteCancelled clears busy without reporting a status message`() =
        runTest {
            viewModel.onExportBackupRequested { }

            viewModel.onExportWriteCancelled()

            assertEquals(false, viewModel.uiState.value.isBusy)
            assertNull(viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `onExportWriteFailed clears busy and reports OperationFailed`() =
        runTest {
            viewModel.onExportBackupRequested { }

            viewModel.onExportWriteFailed()

            assertEquals(false, viewModel.uiState.value.isBusy)
            assertEquals(BackupStatusMessage.OperationFailed, viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `onRestoreFileReadFailed reports OperationFailed without staging a restore`() =
        runTest {
            viewModel.onRestoreFileReadFailed()

            assertEquals(BackupStatusMessage.OperationFailed, viewModel.uiState.value.statusMessage)
            assertNull(viewModel.uiState.value.pendingRestoreJson)
        }

    @Test
    fun `restore round-trip succeeds after a valid export is confirmed`() =
        runTest {
            var exported: String? = null
            viewModel.onExportBackupRequested { exported = it }

            viewModel.onRestoreFilePicked(exported!!)
            assertNotNull(viewModel.uiState.value.pendingRestoreJson)

            viewModel.onRestoreConfirmed()

            assertNull(viewModel.uiState.value.pendingRestoreJson)
            assertEquals(BackupStatusMessage.RestoreSucceeded, viewModel.uiState.value.statusMessage)
            assertEquals(1, restNotificationCanceller.cancelCount)
        }

    @Test
    fun `onRestoreCancelled clears the pending restore without restoring`() =
        runTest {
            viewModel.onRestoreFilePicked("{}")
            assertNotNull(viewModel.uiState.value.pendingRestoreJson)

            viewModel.onRestoreCancelled()

            assertNull(viewModel.uiState.value.pendingRestoreJson)
            assertNull(viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `restoring an invalid backup reports InvalidBackup`() =
        runTest {
            viewModel.onRestoreFilePicked("not a real token")

            viewModel.onRestoreConfirmed()

            assertEquals(BackupStatusMessage.InvalidBackup, viewModel.uiState.value.statusMessage)
            assertEquals(0, restNotificationCanceller.cancelCount)
        }

    @Test
    fun `a restore that fails leaves the rest notification`() =
        runTest {
            var exported: String? = null
            viewModel.onExportBackupRequested { exported = it }
            backupRepository.nextReplaceAllFailure = BackupRestoreError.Unavailable
            viewModel.onRestoreFilePicked(exported!!)

            viewModel.onRestoreConfirmed()

            assertEquals(BackupStatusMessage.OperationFailed, viewModel.uiState.value.statusMessage)
            assertEquals(0, restNotificationCanceller.cancelCount)
        }

    @Test
    fun `onStatusMessageShown clears the status message`() =
        runTest {
            viewModel.onExportBackupRequested { }
            viewModel.onExportWriteSucceeded(BackupExportKind.BACKUP)
            assertNotNull(viewModel.uiState.value.statusMessage)

            viewModel.onStatusMessageShown()

            assertNull(viewModel.uiState.value.statusMessage)
        }

    @Test
    fun `a backup export that is written records last_backup_at`() =
        runTest {
            assertNull(settingsRepository.get().lastBackupAt)
            viewModel.onExportBackupRequested { }

            viewModel.onExportWriteSucceeded(BackupExportKind.BACKUP)

            assertEquals(Instant.parse("2026-10-03T12:00:00Z"), settingsRepository.get().lastBackupAt)
            assertEquals(Instant.parse("2026-10-03T12:00:00Z"), viewModel.uiState.value.lastBackupAt)
            assertEquals(true, viewModel.uiState.value.isLastBackupLoaded)
        }

    @Test
    fun `the hero state starts from the stored last_backup_at`() =
        runTest {
            val stored = Instant.parse("2026-09-30T21:04:00Z")
            val repository = InMemorySettingsRepository(AppSettings.DEFAULT.copy(lastBackupAt = stored))

            val model = BackupViewModel(exportBackup, restoreBackup, exportWorkoutHistoryCsv, restNotificationCanceller, repository, clock)

            assertEquals(stored, model.uiState.value.lastBackupAt)
            assertEquals(true, model.uiState.value.isLastBackupLoaded)
        }

    @Test
    fun `a CSV export never records last_backup_at`() =
        runTest {
            viewModel.onCsvExportRequested { }

            viewModel.onExportWriteSucceeded(BackupExportKind.CSV)

            assertNull(settingsRepository.get().lastBackupAt)
            assertNull(viewModel.uiState.value.lastBackupAt)
        }

    @Test
    fun `a cancelled or failed backup export never records last_backup_at`() =
        runTest {
            viewModel.onExportBackupRequested { }
            viewModel.onExportWriteCancelled()
            viewModel.onExportBackupRequested { }
            viewModel.onExportWriteFailed()

            assertNull(settingsRepository.get().lastBackupAt)
        }

    @Test
    fun `a failed last_backup_at write does not fail the export`() =
        runTest {
            settingsRepository.nextUpdateFailure = SettingsPersistenceError.Unavailable
            viewModel.onExportBackupRequested { }

            viewModel.onExportWriteSucceeded(BackupExportKind.BACKUP)

            assertEquals(BackupStatusMessage.ExportSucceeded, viewModel.uiState.value.statusMessage)
            assertEquals(false, viewModel.uiState.value.isBusy)
            assertNull(settingsRepository.get().lastBackupAt)
        }

    @Test
    fun `a settings read failure leaves the screen alive with the hero loaded and export still working`() =
        runTest {
            val failing =
                object : SettingsRepository {
                    override fun observe(): Flow<AppSettings> = flow { throw IllegalStateException("settings row unreadable") }

                    override suspend fun get(): AppSettings = AppSettings.DEFAULT

                    override suspend fun update(transform: (AppSettings) -> AppSettings) = DomainResult.Success(Unit)
                }

            val failedViewModel =
                BackupViewModel(exportBackup, restoreBackup, exportWorkoutHistoryCsv, restNotificationCanceller, failing, clock)

            assertEquals(true, failedViewModel.uiState.value.isLastBackupLoaded)
            assertNull(failedViewModel.uiState.value.lastBackupAt)
            var delivered: String? = null
            failedViewModel.onExportBackupRequested { delivered = it }
            assertNotNull(delivered)
        }

    /** Guards the ViewModel only: the in-memory backup fake cannot reach the settings fake, so a repository-level write is the instrumented restore test's to catch. */
    @Test
    fun `a successful restore does not make the ViewModel write last_backup_at`() =
        runTest {
            var exported: String? = null
            viewModel.onExportBackupRequested { exported = it }
            viewModel.onExportWriteSucceeded(BackupExportKind.BACKUP)
            val recorded = settingsRepository.get().lastBackupAt
            viewModel.onRestoreFilePicked(exported!!)

            viewModel.onRestoreConfirmed()

            assertEquals(recorded, settingsRepository.get().lastBackupAt)
        }
}
