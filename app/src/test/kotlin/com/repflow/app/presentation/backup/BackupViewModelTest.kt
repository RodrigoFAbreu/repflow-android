package com.repflow.app.presentation.backup

import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.InMemoryBackupRepository
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val exportWorkoutHistoryCsv = ExportWorkoutHistoryCsv(workoutRepository)

    private val viewModel = BackupViewModel(exportBackup, restoreBackup, exportWorkoutHistoryCsv)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
}
