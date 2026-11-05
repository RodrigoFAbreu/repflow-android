package com.repflow.app.presentation.backup

import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.ExportWorkoutHistoryCsv
import com.repflow.app.application.backup.InMemoryBackupRepository
import com.repflow.app.application.backup.InMemoryPreRestoreSnapshotStore
import com.repflow.app.application.backup.InMemoryTrainingDataRepository
import com.repflow.app.application.backup.PreRestoreCopyLock
import com.repflow.app.application.backup.PreRestoreSnapshot
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.backup.SavePreRestoreCopy
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.presentation.workout.RecordingRestNotificationCanceller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The safety copy on the Backup screen, and the outcome of a restore that outlives its screen. Every
 * ViewModel here shares the store, the lock and the slot, as Hilt's singletons do in the app, and the
 * eager `Main` dispatcher runs each one up to its first real wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelSafetyCopyTest {
    private val workoutRepository = InMemoryWorkoutRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val backupRepository = InMemoryBackupRepository()
    private var workoutsUnreadable = false
    private val workoutsForExport =
        object : WorkoutRepository by workoutRepository {
            override suspend fun findAllForBackup(): List<WorkoutSession> =
                if (workoutsUnreadable) error("session row unreadable") else workoutRepository.findAllForBackup()
        }
    private val exportBackup =
        ExportBackup(
            exerciseRepository = InMemoryExerciseRepository(),
            trainingPlanRepository = trainingPlanRepository,
            workoutRepository = workoutsForExport,
            recoveryRepository = InMemoryRecoveryRepository(),
            futsalRepository = InMemoryFutsalRepository(),
            progressionRepository = InMemoryProgressionRecommendationRepository(),
            backupRepository = backupRepository,
        )
    private val trainingData = InMemoryTrainingDataRepository()
    private val snapshotStore = InMemoryPreRestoreSnapshotStore(now = NEW_COPY_AT)
    private val copyLock = PreRestoreCopyLock()
    private val outcomeSlot = RestoreOutcomeSlot()
    private val preRestoreCopy = SavePreRestoreCopy(trainingData, exportBackup, backupRepository, snapshotStore, copyLock)
    private val restoreBackup = RestoreBackup(backupRepository, preRestoreCopy)
    private val gated = GatedBackupRepository()
    private val gatedRestore = RestoreBackup(gated, preRestoreCopy)
    private val exportWorkoutHistoryCsv =
        ExportWorkoutHistoryCsv(workoutRepository, ObserveTrainingPlanVersionLabels(trainingPlanRepository))
    private val settingsRepository = InMemorySettingsRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(restore: RestoreBackup = restoreBackup) =
        BackupViewModel(
            exportBackup,
            restore,
            exportWorkoutHistoryCsv,
            RecordingRestNotificationCanceller(),
            settingsRepository,
            workoutRepository,
            snapshotStore,
            copyLock,
            trainingData,
            outcomeSlot,
            RestoreInProgress(),
            FixedClock(Instant.parse("2026-10-03T12:00:00Z")),
        )

    /** The copy on offer, holding valid text: the fake's text is a token for the snapshot it serialized. */
    private suspend fun holdOldCopy(): PreRestoreSnapshot = PreRestoreSnapshot(exportBackup(), OLD_COPY_AT).also { snapshotStore.old = it }

    private suspend fun BackupViewModel.restoreAFile() {
        onRestoreFilePicked(exportBackup())
        onRestoreConfirmed()
    }

    /** A restore held at the gate, whose screen has gone. */
    private suspend fun heldRestoreWithItsScreenGone() {
        val first = buildViewModel(restore = gatedRestore)
        first.restoreAFile()
        assertEquals(1, gated.replaceAllCalls)
        first.viewModelScope.cancel()
    }

    @Test
    fun `the copy's time is exposed after init and after a restore`() =
        runTest {
            val model = buildViewModel()
            assertNull("With no copy there is no card", model.uiState.value.preRestoreSnapshotAt)
            assertFalse(model.uiState.value.isBusy)

            holdOldCopy()
            assertEquals(OLD_COPY_AT, buildViewModel().uiState.value.preRestoreSnapshotAt)

            model.restoreAFile()
            assertEquals(NEW_COPY_AT, model.uiState.value.preRestoreSnapshotAt)
            assertFalse(model.uiState.value.isBusy)
        }

    @Test
    fun `opening the screen and finishing a restore read the time, and never the whole copy`() =
        runTest {
            holdOldCopy()
            val model = buildViewModel()
            model.restoreAFile()

            assertTrue(snapshotStore.calls.contains("takenAt"))
            assertFalse(snapshotStore.calls.contains("load"))
        }

    @Test
    fun `tapping the action stages the copy's text, and confirming restores it and writes a new copy`() =
        runTest {
            val old = holdOldCopy()
            val model = buildViewModel()

            model.onRestoreSnapshotRequested()

            assertEquals(old.json, model.uiState.value.pendingRestoreJson)
            assertEquals(OLD_COPY_AT, model.uiState.value.pendingRestoreSnapshotAt)
            assertNull("Nothing is replaced before the confirmation", backupRepository.lastReplacedWith)

            model.onRestoreConfirmed()

            assertNull(model.uiState.value.pendingRestoreJson)
            assertNull(model.uiState.value.pendingRestoreSnapshotAt)
            assertEquals(parsed(old.json), backupRepository.lastReplacedWith)
            assertEquals(BackupStatusMessage.RestoreSucceeded, model.uiState.value.statusMessage)
            assertEquals(NEW_COPY_AT, model.uiState.value.preRestoreSnapshotAt)
            assertNotEquals("The two states swap", old.json, snapshotStore.old?.json)
        }

    @Test
    fun `a second tap on the card while the copy loads is ignored, and every button waits for the load`() =
        runTest {
            val old = holdOldCopy()
            val model = buildViewModel()
            val release = CompletableDeferred<Unit>()
            val holder = launch(UnconfinedTestDispatcher(testScheduler)) { copyLock.withLock { release.await() } }

            model.onRestoreSnapshotRequested()
            assertTrue(model.uiState.value.isBusy)
            model.onRestoreSnapshotRequested()

            release.complete(Unit)
            holder.join()
            testScheduler.advanceUntilIdle()

            assertEquals(1, snapshotStore.calls.count { it == "load" })
            assertEquals(old.json, model.uiState.value.pendingRestoreJson)
            assertFalse(model.uiState.value.isBusy)
        }

    @Test
    fun `cancelling the copy's confirmation clears its time along with its text`() =
        runTest {
            holdOldCopy()
            val model = buildViewModel()
            model.onRestoreSnapshotRequested()

            model.onRestoreCancelled()

            assertNull(model.uiState.value.pendingRestoreJson)
            assertNull(model.uiState.value.pendingRestoreSnapshotAt)
            assertEquals(OLD_COPY_AT, model.uiState.value.preRestoreSnapshotAt)
        }

    @Test
    fun `a file staged over a pending copy is not treated as the copy`() =
        runTest {
            holdOldCopy()
            val model = buildViewModel()
            model.onRestoreSnapshotRequested()

            model.onRestoreFilePicked(exportBackup())

            assertNull(model.uiState.value.pendingRestoreSnapshotAt)
        }

    @Test
    fun `a restore of the copy that fails leaves the old copy byte-identical and still offered`() =
        runTest {
            val old = holdOldCopy()
            val model = buildViewModel()
            model.onRestoreSnapshotRequested()
            backupRepository.nextReplaceAllFailure = BackupRestoreError.Unavailable

            model.onRestoreConfirmed()

            assertEquals(BackupStatusMessage.OperationFailed, model.uiState.value.statusMessage)
            assertEquals(old, snapshotStore.old)
            assertNull(snapshotStore.new)
            assertEquals(OLD_COPY_AT, model.uiState.value.preRestoreSnapshotAt)
            assertFalse(model.uiState.value.isBusy)

            model.onRestoreSnapshotRequested()
            assertEquals(old.json, model.uiState.value.pendingRestoreJson)
        }

    @Test
    fun `a restore whose export read throws shows OperationFailed, restores nothing and does not crash`() =
        runTest {
            val old = holdOldCopy()
            val model = buildViewModel()
            model.onRestoreFilePicked(exportBackup())
            workoutsUnreadable = true

            model.onRestoreConfirmed()

            assertEquals(BackupStatusMessage.OperationFailed, model.uiState.value.statusMessage)
            assertNull(backupRepository.lastReplacedWith)
            assertEquals(old, snapshotStore.old)
            assertFalse(model.uiState.value.isBusy)
        }

    @Test
    fun `a copy that vanished shows OperationFailed, stages nothing and takes its card down`() =
        runTest {
            holdOldCopy()
            val model = buildViewModel()
            snapshotStore.old = null

            model.onRestoreSnapshotRequested()

            assertEquals(BackupStatusMessage.OperationFailed, model.uiState.value.statusMessage)
            assertNull(model.uiState.value.pendingRestoreJson)
            assertNull(model.uiState.value.preRestoreSnapshotAt)
            assertFalse(model.uiState.value.isBusy)
        }

    @Test
    fun `the flag is set for both ways in, and a failed read leaves it true`() =
        runTest {
            holdOldCopy()
            val model = buildViewModel()
            val file = exportBackup()

            trainingData.hasTrainingData = false
            model.onRestoreFilePicked(file)
            assertFalse(model.uiState.value.hasTrainingData)
            trainingData.readFailure = IllegalStateException("database unreadable")
            model.onRestoreFilePicked(file)
            assertTrue(model.uiState.value.hasTrainingData)
            model.onRestoreCancelled()

            trainingData.readFailure = null
            model.onRestoreSnapshotRequested()
            assertFalse(model.uiState.value.hasTrainingData)
            trainingData.readFailure = IllegalStateException("database unreadable")
            model.onRestoreSnapshotRequested()
            assertTrue(model.uiState.value.hasTrainingData)
        }

    @Test
    fun `a restore from a file and a restore of the copy each leave lastBackupAt unchanged`() =
        runTest {
            val recorded = Instant.parse("2026-09-30T21:04:00Z")
            settingsRepository.update { AppSettings.DEFAULT.copy(lastBackupAt = recorded) }
            val model = buildViewModel()

            model.restoreAFile()
            assertEquals(BackupStatusMessage.RestoreSucceeded, model.uiState.value.statusMessage)
            assertEquals(recorded, settingsRepository.get().lastBackupAt)
            model.onStatusMessageShown()

            model.onRestoreSnapshotRequested()
            model.onRestoreConfirmed()
            assertEquals(BackupStatusMessage.RestoreSucceeded, model.uiState.value.statusMessage)
            assertEquals(recorded, settingsRepository.get().lastBackupAt)
        }

    @Test
    fun `a ViewModel built during another's restore is busy, shows no card and stages nothing`() =
        runTest {
            holdOldCopy()
            heldRestoreWithItsScreenGone()

            val reopened = buildViewModel(restore = gatedRestore)
            assertTrue(reopened.uiState.value.isBusy)
            assertNull(reopened.uiState.value.preRestoreSnapshotAt)

            reopened.onRestoreFilePicked(exportBackup())
            reopened.onRestoreSnapshotRequested()

            assertNull(reopened.uiState.value.pendingRestoreJson)
            assertNull(reopened.uiState.value.pendingRestoreSnapshotAt)
            assertFalse("Staging the copy must wait for the restore", snapshotStore.calls.contains("load"))
        }

    @Test
    fun `when that restore succeeds the reopened screen is idle and shows the new copy's time`() =
        runTest {
            holdOldCopy()
            heldRestoreWithItsScreenGone()
            val reopened = buildViewModel(restore = gatedRestore)

            gated.open(DomainResult.Success(Unit))

            assertFalse(reopened.uiState.value.isBusy)
            assertEquals(NEW_COPY_AT, reopened.uiState.value.preRestoreSnapshotAt)
        }

    @Test
    fun `when that restore fails the reopened screen shows the old copy and stages its text`() =
        runTest {
            val old = holdOldCopy()
            heldRestoreWithItsScreenGone()
            val reopened = buildViewModel(restore = gatedRestore)

            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))

            assertFalse(reopened.uiState.value.isBusy)
            assertEquals(OLD_COPY_AT, reopened.uiState.value.preRestoreSnapshotAt)

            reopened.onRestoreSnapshotRequested()
            assertEquals(old.json, reopened.uiState.value.pendingRestoreJson)
        }

    @Test
    fun `a screen reopened before a failing restore ends gets the failure when it ends`() =
        runTest {
            val old = holdOldCopy()
            heldRestoreWithItsScreenGone()
            val reopened = buildViewModel(restore = gatedRestore)
            assertTrue(reopened.uiState.value.isBusy)
            assertNull(reopened.uiState.value.statusMessage)

            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))

            assertFalse(reopened.uiState.value.isBusy)
            assertEquals(BackupStatusMessage.OperationFailed, reopened.uiState.value.statusMessage)
            assertEquals(OLD_COPY_AT, reopened.uiState.value.preRestoreSnapshotAt)
            assertEquals(old.json, snapshotStore.load()?.json)
        }

    @Test
    fun `a screen opened after a failing restore ended gets the failure at once`() =
        runTest {
            holdOldCopy()
            heldRestoreWithItsScreenGone()

            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))
            val reopened = buildViewModel(restore = gatedRestore)

            assertFalse(reopened.uiState.value.isBusy)
            assertEquals(BackupStatusMessage.OperationFailed, reopened.uiState.value.statusMessage)
        }

    @Test
    fun `a screen reopened before a successful restore ends says so when it ends`() =
        runTest {
            holdOldCopy()
            heldRestoreWithItsScreenGone()
            val reopened = buildViewModel(restore = gatedRestore)
            assertNull(reopened.uiState.value.statusMessage)

            gated.open(DomainResult.Success(Unit))

            assertEquals(BackupStatusMessage.RestoreSucceeded, reopened.uiState.value.statusMessage)
            assertEquals(NEW_COPY_AT, reopened.uiState.value.preRestoreSnapshotAt)
        }

    @Test
    fun `a screen opened after a successful restore ended says so at once`() =
        runTest {
            holdOldCopy()
            heldRestoreWithItsScreenGone()

            gated.open(DomainResult.Success(Unit))
            val reopened = buildViewModel(restore = gatedRestore)

            assertEquals(BackupStatusMessage.RestoreSucceeded, reopened.uiState.value.statusMessage)
            assertEquals(NEW_COPY_AT, reopened.uiState.value.preRestoreSnapshotAt)
        }

    @Test
    fun `an outcome is shown once, even after a back press during its snackbar`() =
        runTest {
            heldRestoreWithItsScreenGone()
            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))
            val second = buildViewModel(restore = gatedRestore)

            second.onStatusMessageShowing()

            assertNull(buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    @Test
    fun `an outcome is shown once after its snackbar ends too`() =
        runTest {
            heldRestoreWithItsScreenGone()
            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))
            val second = buildViewModel(restore = gatedRestore)

            second.onStatusMessageShowing()
            second.onStatusMessageShown()

            assertNull(second.uiState.value.statusMessage)
            assertNull(buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    @Test
    fun `an outcome no snackbar has started for waits for the next screen`() =
        runTest {
            val first = buildViewModel(restore = gatedRestore)
            first.restoreAFile()

            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))
            assertEquals(BackupStatusMessage.OperationFailed, first.uiState.value.statusMessage)
            first.viewModelScope.cancel()

            assertEquals(BackupStatusMessage.OperationFailed, buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    /** Two equal outcomes on one open screen, the second while the first's snackbar is up. */
    private suspend fun secondEqualOutcomeDuringTheSnackbar(result: DomainResult<Unit, BackupRestoreError>): BackupViewModel {
        gated.open(result)
        val model = buildViewModel(restore = gatedRestore)
        model.restoreAFile()
        model.onStatusMessageShowing()
        model.restoreAFile()
        return model
    }

    @Test
    fun `a failure repeating during its snackbar merges, and the next one still shows`() =
        runTest {
            val model = secondEqualOutcomeDuringTheSnackbar(DomainResult.Failure(BackupRestoreError.Unavailable))
            model.onStatusMessageShown()
            assertNull(model.uiState.value.statusMessage)

            model.restoreAFile()

            assertEquals(BackupStatusMessage.OperationFailed, model.uiState.value.statusMessage)
        }

    @Test
    fun `a failure that merged into a snackbar is not reported again at a later visit`() =
        runTest {
            val model = secondEqualOutcomeDuringTheSnackbar(DomainResult.Failure(BackupRestoreError.Unavailable))
            model.onStatusMessageShown()
            assertNull(model.uiState.value.statusMessage)

            assertNull(buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    @Test
    fun `a success repeating during its snackbar merges, and the next one still shows`() =
        runTest {
            val model = secondEqualOutcomeDuringTheSnackbar(DomainResult.Success(Unit))
            model.onStatusMessageShown()
            assertNull(model.uiState.value.statusMessage)

            model.restoreAFile()

            assertEquals(BackupStatusMessage.RestoreSucceeded, model.uiState.value.statusMessage)
        }

    @Test
    fun `a success that merged into a snackbar is not reported again at a later visit`() =
        runTest {
            val model = secondEqualOutcomeDuringTheSnackbar(DomainResult.Success(Unit))
            model.onStatusMessageShown()
            assertNull(model.uiState.value.statusMessage)

            assertNull(buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    @Test
    fun `leaving during the snackbar leaves the merged outcome, which never got a snackbar of its own, for the next visit`() =
        runTest {
            secondEqualOutcomeDuringTheSnackbar(DomainResult.Failure(BackupRestoreError.Unavailable))

            assertEquals(BackupStatusMessage.OperationFailed, buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    @Test
    fun `a different outcome during a snackbar takes over, and is shown once`() =
        runTest {
            gated.open(DomainResult.Failure(BackupRestoreError.Unavailable))
            val model = buildViewModel(restore = gatedRestore)
            model.restoreAFile()
            model.onStatusMessageShowing()

            gated.open(DomainResult.Success(Unit))
            model.restoreAFile()
            assertEquals(BackupStatusMessage.RestoreSucceeded, model.uiState.value.statusMessage)

            model.onStatusMessageShowing()
            model.onStatusMessageShown()

            assertNull(buildViewModel(restore = gatedRestore).uiState.value.statusMessage)
        }

    private fun parsed(json: String): BackupSnapshot = (backupRepository.parseSnapshot(json) as DomainResult.Success).value

    /** A restore that waits at a gate and then returns the result the test gave it, as does every later one. */
    private inner class GatedBackupRepository : BackupRepository by backupRepository {
        private var gate = CompletableDeferred<DomainResult<Unit, BackupRestoreError>>()
        var replaceAllCalls = 0
            private set

        fun open(result: DomainResult<Unit, BackupRestoreError>) {
            if (!gate.complete(result)) gate = CompletableDeferred(result)
        }

        override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
            replaceAllCalls++
            return gate.await()
        }
    }

    private companion object {
        val OLD_COPY_AT: Instant = Instant.parse("2026-09-20T07:00:00Z")
        val NEW_COPY_AT: Instant = Instant.parse("2026-10-02T08:15:00Z")
    }
}
