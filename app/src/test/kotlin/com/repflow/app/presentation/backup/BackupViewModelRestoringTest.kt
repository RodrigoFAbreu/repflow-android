package com.repflow.app.presentation.backup

import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.InMemoryBackupRepository
import com.repflow.app.application.backup.InMemoryPreRestoreSnapshotStore
import com.repflow.app.application.backup.InMemoryTrainingDataRepository
import com.repflow.app.application.backup.PreRestoreCopyLock
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.backup.SavePreRestoreCopy
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.presentation.workout.RecordingRestNotificationCanceller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * When the Restoring screen's flag rises and falls. As in `BackupViewModelSafetyCopyTest`, the store,
 * the lock, the slot and the flag are shared as Hilt's singletons are, and the eager `Main` dispatcher
 * runs each ViewModel up to its first real wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelRestoringTest {
    private val workoutRepository = InMemoryWorkoutRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val backupRepository = InMemoryBackupRepository()
    private val exportBackup =
        ExportBackup(
            exerciseRepository = InMemoryExerciseRepository(),
            trainingPlanRepository = trainingPlanRepository,
            workoutRepository = workoutRepository,
            recoveryRepository = InMemoryRecoveryRepository(),
            futsalRepository = InMemoryFutsalRepository(),
            progressionRepository = InMemoryProgressionRecommendationRepository(),
            backupRepository = backupRepository,
        )
    private val trainingData = InMemoryTrainingDataRepository()
    private val snapshotStore = InMemoryPreRestoreSnapshotStore()
    private val copyLock = PreRestoreCopyLock()
    private val outcomeSlot = RestoreOutcomeSlot()
    private val restoreInProgress = RestoreInProgress()
    private val preRestoreCopy = SavePreRestoreCopy(trainingData, exportBackup, backupRepository, snapshotStore, copyLock)
    private val exportWorkoutHistoryCsv =
        ExportWorkoutHistoryCsv(workoutRepository, ObserveTrainingPlanVersionLabels(trainingPlanRepository))

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(repository: BackupRepository = backupRepository) =
        BackupViewModel(
            exportBackup,
            RestoreBackup(repository, preRestoreCopy),
            exportWorkoutHistoryCsv,
            RecordingRestNotificationCanceller(),
            InMemorySettingsRepository(),
            workoutRepository,
            snapshotStore,
            copyLock,
            trainingData,
            outcomeSlot,
            restoreInProgress,
            FixedClock(Instant.parse("2026-10-03T12:00:00Z")),
        )

    @Test
    fun `the flag is up from the confirmation until the restore ends`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val held =
                object : BackupRepository by backupRepository {
                    override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
                        gate.await()
                        return backupRepository.replaceAll(snapshot)
                    }
                }
            val model = buildViewModel(held)
            model.onRestoreFilePicked(exportBackup())
            assertFalse("Picking a file and reading the confirmation blocks nothing", restoreInProgress.active.value)

            model.onRestoreConfirmed()
            assertTrue(restoreInProgress.active.value)

            gate.complete(Unit)
            assertFalse(restoreInProgress.active.value)
            assertEquals(BackupStatusMessage.RestoreSucceeded, outcomeSlot.outcome.value)
        }

    /** Checking after `onRestoreConfirmed` can't show this: `UNDISPATCHED` runs the body inside the call. */
    @Test
    fun `the flag is already up when the restore first runs`() =
        runTest {
            var activeAtFirstCall: Boolean? = null
            val recording =
                object : BackupRepository by backupRepository {
                    override fun parseSnapshot(json: String): DomainResult<BackupSnapshot, BackupValidationError> {
                        activeAtFirstCall = restoreInProgress.active.value
                        return backupRepository.parseSnapshot(json)
                    }
                }
            val model = buildViewModel(recording)
            model.onRestoreFilePicked(exportBackup())

            model.onRestoreConfirmed()

            assertEquals(true, activeAtFirstCall)
        }

    @Test
    fun `the flag lifts after a rejected file and after a failed restore`() =
        runTest {
            val model = buildViewModel()

            model.onRestoreFilePicked(NOT_A_BACKUP)
            model.onRestoreConfirmed()
            assertFalse(restoreInProgress.active.value)
            assertEquals(BackupStatusMessage.InvalidBackup, outcomeSlot.outcome.value)

            snapshotStore.nextSaveFails = true
            model.onRestoreFilePicked(exportBackup())
            model.onRestoreConfirmed()
            assertFalse(restoreInProgress.active.value)
            assertEquals(BackupStatusMessage.OperationFailed, outcomeSlot.outcome.value)
        }

    /**
     * The exception leaves `viewModelScope` with no handler and `runTest` reports it when the test ends,
     * so `assertThrows` goes around `runTest`. It stays inside `runTest`, because an uncaught exception
     * outside it can be carried into the next `runTest` in the same JVM.
     */
    @Test
    fun `the flag lifts when the restore throws`() {
        assertThrows(IllegalStateException::class.java) {
            runTest {
                val throwing =
                    object : BackupRepository by backupRepository {
                        override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> =
                            error("the restore broke")
                    }
                val model = buildViewModel(throwing)
                model.onRestoreFilePicked(exportBackup())

                model.onRestoreConfirmed()

                assertFalse(restoreInProgress.active.value)
            }
        }
    }

    /** An invalid file skips the lock inside the restore: a valid one would wait for it and never reach `report`. */
    @Test
    fun `the flag does not wait for the copy read`() =
        runTest {
            val model = buildViewModel()
            model.onRestoreFilePicked(NOT_A_BACKUP) // First, since staging takes the lock too.
            val release = CompletableDeferred<Unit>()
            val holder = launch(UnconfinedTestDispatcher(testScheduler)) { copyLock.withLock { release.await() } }

            model.onRestoreConfirmed()

            assertFalse(restoreInProgress.active.value)
            assertEquals(BackupStatusMessage.InvalidBackup, outcomeSlot.outcome.value)
            assertTrue("The copy's time has not been read", model.uiState.value.isBusy)

            release.complete(Unit)
            holder.join()
            testScheduler.advanceUntilIdle()

            assertFalse(model.uiState.value.isBusy)
        }

    private companion object {
        const val NOT_A_BACKUP = "not a backup"
    }
}
