package com.repflow.app.presentation.backup

import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.EraseAllData
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
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.settings.SettingsPersistenceError
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.workout.RecordingRestNotificationCanceller
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val trainingData = InMemoryTrainingDataRepository()
    private val snapshotStore = InMemoryPreRestoreSnapshotStore()
    private val copyLock = PreRestoreCopyLock()
    private val preRestoreCopy = SavePreRestoreCopy(trainingData, exportBackup, backupRepository, snapshotStore, copyLock)
    private val eraseAllData = EraseAllData(trainingData, snapshotStore, copyLock)
    private val restoreBackup = RestoreBackup(backupRepository, preRestoreCopy)
    private val exportWorkoutHistoryCsv =
        ExportWorkoutHistoryCsv(workoutRepository, ObserveTrainingPlanVersionLabels(trainingPlanRepository))

    private val restNotificationCanceller = RecordingRestNotificationCanceller()
    private val restoreOutcomeSlot = RestoreOutcomeSlot()
    private val settingsRepository = InMemorySettingsRepository()
    private val clock = FixedClock(Instant.parse("2026-10-03T12:00:00Z"))
    private lateinit var viewModel: BackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Built after the Main dispatcher is set: the ViewModel starts observing the settings in `init`.
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        export: ExportBackup = exportBackup,
        settings: SettingsRepository = settingsRepository,
        workouts: WorkoutRepository = workoutRepository,
        restore: RestoreBackup = restoreBackup,
    ) = BackupViewModel(
        export,
        restore,
        exportWorkoutHistoryCsv,
        restNotificationCanceller,
        settings,
        workouts,
        snapshotStore,
        copyLock,
        trainingData,
        restoreOutcomeSlot,
        RestoreInProgress(),
        clock,
    )

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

            val model = buildViewModel(settings = repository)

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
    fun `a suspended last_backup_at write still lands after the ViewModel is cleared`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val suspending =
                object : SettingsRepository by settingsRepository {
                    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit, SettingsPersistenceError> {
                        gate.await()
                        return settingsRepository.update(transform)
                    }
                }
            val model = buildViewModel(settings = suspending)
            model.onExportBackupRequested { }

            model.onExportWriteSucceeded(BackupExportKind.BACKUP)
            // Leaving the Backup route clears the ViewModel, which cancels viewModelScope, mid-write.
            model.viewModelScope.cancel()
            gate.complete(Unit)

            assertEquals(Instant.parse("2026-10-03T12:00:00Z"), settingsRepository.get().lastBackupAt)
        }

    @Test
    fun `a last_backup_at write still lands when the scope is cancelled before a queued dispatch runs`() {
        // A queued dispatcher (unlike the eager one set up for the other tests) holds the launched body
        // until pending tasks are run, so this covers cancellation before the coroutine first executes.
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        runTest(dispatcher) {
            val model = buildViewModel()

            model.onExportWriteSucceeded(BackupExportKind.BACKUP)
            model.viewModelScope.cancel()
            testScheduler.advanceUntilIdle()

            assertEquals(Instant.parse("2026-10-03T12:00:00Z"), settingsRepository.get().lastBackupAt)
        }
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

            val failedViewModel = buildViewModel(settings = failing)

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

    @Test
    fun `hasRunningWorkout follows the running session`() =
        runTest {
            assertFalse(viewModel.uiState.value.hasRunningWorkout)

            val startedAt = Instant.parse("2026-10-03T11:00:00Z")
            val running = WorkoutSession.start(WorkoutSessionId("running"), null, startedAt)
            workoutRepository.insert(running)
            assertTrue(viewModel.uiState.value.hasRunningWorkout)

            workoutRepository.update((running.complete(startedAt.plusSeconds(3600)) as DomainResult.Success).value)
            assertFalse(viewModel.uiState.value.hasRunningWorkout)
        }

    @Test
    fun `a failed read of the running workout still warns, and the restore can still be staged`() =
        runTest {
            val model = buildViewModel(workouts = UnreadableWorkoutRepository(workoutRepository))

            assertTrue(model.uiState.value.hasRunningWorkout)

            model.onRestoreFilePicked("{}")
            assertEquals("{}", model.uiState.value.pendingRestoreJson)
            assertTrue(model.uiState.value.hasRunningWorkout)
        }

    @Test
    fun `a restore staged before the first read of the running workout warns, until no workout is confirmed`() =
        runTest {
            val firstRead = CompletableDeferred<WorkoutSession?>()
            val delayed =
                object : WorkoutRepository by workoutRepository {
                    override fun observeActiveSession(): Flow<WorkoutSession?> = flow { emit(firstRead.await()) }
                }
            val model = buildViewModel(workouts = delayed)

            model.onRestoreFilePicked("{}")
            assertEquals("{}", model.uiState.value.pendingRestoreJson)
            assertTrue(model.uiState.value.hasRunningWorkout)

            firstRead.complete(null)
            assertEquals("{}", model.uiState.value.pendingRestoreJson)
            assertFalse(model.uiState.value.hasRunningWorkout)
        }

    @Test
    fun `a read that fails after finding no workout warns again`() =
        runTest {
            val nextRead = CompletableDeferred<Unit>()
            val failingLater =
                object : WorkoutRepository by workoutRepository {
                    override fun observeActiveSession(): Flow<WorkoutSession?> =
                        flow {
                            emit(null)
                            nextRead.await()
                            error("session row unreadable")
                        }
                }
            val model = buildViewModel(workouts = failingLater)
            assertFalse(model.uiState.value.hasRunningWorkout)

            nextRead.complete(Unit)

            assertTrue(model.uiState.value.hasRunningWorkout)
        }

    @Test
    fun `an export that can't read a workout reports OperationFailed and opens no file picker`() =
        runTest {
            val unreadable = UnreadableWorkoutRepository(workoutRepository)
            val failingExport =
                ExportBackup(
                    exerciseRepository = exerciseRepository,
                    trainingPlanRepository = trainingPlanRepository,
                    workoutRepository = unreadable,
                    recoveryRepository = recoveryRepository,
                    futsalRepository = futsalRepository,
                    progressionRepository = progressionRepository,
                    backupRepository = backupRepository,
                )
            val model = buildViewModel(export = failingExport, workouts = unreadable)
            var delivered: String? = null

            model.onExportBackupRequested { delivered = it }

            assertNull(delivered)
            assertEquals(false, model.uiState.value.isBusy)
            assertEquals(BackupStatusMessage.OperationFailed, model.uiState.value.statusMessage)
        }

    @Test
    fun `leaving the screen during a restore still finishes it, promotes the copy and cancels the rest notification`() =
        runTest {
            holdExercise("A")
            val gated = GatedBackupRepository()
            val model = buildViewModel(restore = RestoreBackup(gated, preRestoreCopy))
            model.onRestoreFilePicked(fileHolding("B"))

            model.onRestoreConfirmed()
            assertEquals(1, gated.replaceAllCalls)
            // Leaving the Backup route clears the ViewModel, which cancels viewModelScope, mid-restore.
            model.viewModelScope.cancel()
            gated.gate.complete(Unit)

            assertEquals(listOf("B"), exerciseNames())
            assertEquals(listOf("A"), exerciseNamesInCopy())
            assertNull(snapshotStore.new)
            assertEquals(1, restNotificationCanceller.cancelCount)
        }

    @Test
    fun `a second restore waits for one that outlived its screen, and the copy holds the data the first left`() =
        runTest {
            holdExercise("A")
            val gated = GatedBackupRepository()
            val restore = RestoreBackup(gated, preRestoreCopy)
            val first = buildViewModel(restore = restore)
            val second = buildViewModel(restore = restore)
            first.onRestoreFilePicked(fileHolding("B"))
            first.onRestoreConfirmed()
            first.viewModelScope.cancel()

            // Staging waits for the lock too, so nothing is staged for the second to confirm yet.
            second.onRestoreFilePicked(fileHolding("C"))
            assertNull(second.uiState.value.pendingRestoreJson)
            second.onRestoreConfirmed()
            assertEquals("replaceAll must not be entered again while the first is held", 1, gated.replaceAllCalls)

            gated.gate.complete(Unit)
            assertNotNull(second.uiState.value.pendingRestoreJson)
            second.onRestoreConfirmed()

            assertEquals(2, gated.replaceAllCalls)
            assertEquals(listOf("C"), exerciseNames())
            assertEquals(listOf("B"), exerciseNamesInCopy())
        }

    @Test
    fun `an erase waits for a restore that outlived its screen, and then leaves neither data nor a copy`() =
        runTest {
            holdExercise("A")
            val gated = GatedBackupRepository()
            val model = buildViewModel(restore = RestoreBackup(gated, preRestoreCopy))
            model.onRestoreFilePicked(fileHolding("B"))
            model.onRestoreConfirmed()
            model.viewModelScope.cancel()
            val storeCallsSoFar = snapshotStore.calls.toList()

            val erase = async(UnconfinedTestDispatcher(testScheduler)) { eraseAllData() }
            assertFalse(erase.isCompleted)
            assertEquals(storeCallsSoFar, snapshotStore.calls)
            assertEquals(0, trainingData.clearCount)

            gated.gate.complete(Unit)

            assertEquals(DomainResult.Success(Unit), erase.await())
            assertFalse(trainingData.hasTrainingData())
            assertNull(snapshotStore.load())
        }

    @Test
    fun `a restore confirmed during an erase waits, finds no training data, writes no copy and still runs`() =
        runTest {
            holdExercise("A")
            val gated = GatedBackupRepository().apply { gate.complete(Unit) }
            val model = buildViewModel(restore = RestoreBackup(gated, preRestoreCopy))
            // Staged before the erase starts, so this test goes on proving the restore ran once staging takes the lock.
            model.onRestoreFilePicked(fileHolding("B"))
            val clearGate = CompletableDeferred<Unit>()
            trainingData.clearGate = clearGate
            val erase = async(UnconfinedTestDispatcher(testScheduler)) { eraseAllData() }

            model.onRestoreConfirmed()
            assertEquals("The restore must wait for the erase", 0, gated.replaceAllCalls)

            clearGate.complete(Unit)

            assertEquals(DomainResult.Success(Unit), erase.await())
            assertEquals(1, gated.replaceAllCalls)
            assertEquals(listOf("B"), exerciseNames())
            assertFalse(snapshotStore.calls.contains("saveNew"))
            assertNull(snapshotStore.load())
        }

    @Test
    fun `an erase cancelled while it waits for a restore deletes nothing`() =
        runTest {
            holdExercise("A")
            val gated = GatedBackupRepository()
            val model = buildViewModel(restore = RestoreBackup(gated, preRestoreCopy))
            model.onRestoreFilePicked(fileHolding("B"))
            model.onRestoreConfirmed()

            val erase = launch(UnconfinedTestDispatcher(testScheduler)) { eraseAllData() }
            erase.cancelAndJoin()
            gated.gate.complete(Unit)

            assertEquals(0, trainingData.clearCount)
            assertFalse(snapshotStore.calls.contains("clear"))
            assertEquals(listOf("A"), exerciseNamesInCopy())
        }

    /** A restore held at [gate], which then applies the snapshot's exercises so the next `ExportBackup` reads them. */
    private inner class GatedBackupRepository : BackupRepository by backupRepository {
        val gate = CompletableDeferred<Unit>()
        var replaceAllCalls = 0
            private set

        override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
            replaceAllCalls++
            gate.await()
            exerciseRepository.replaceAllWith(snapshot.exercises)
            trainingData.hasTrainingData = true
            return DomainResult.Success(Unit)
        }
    }

    private fun exercise(name: String) =
        (
            Exercise.create(
                id = ExerciseId(name),
                name = (ExerciseName.create(name) as DomainResult.Success).value,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.BUILT_IN,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ) as DomainResult.Success
        ).value

    private fun holdExercise(name: String) = exerciseRepository.replaceAllWith(listOf(exercise(name)))

    /** A valid "file": the fake's text is a token for the snapshot it serialized. */
    private fun fileHolding(name: String): String =
        backupRepository.serializeSnapshot(
            (
                BackupSnapshot.create(
                    schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                    exercises = listOf(exercise(name)),
                    trainingPlans = emptyList(),
                    workoutSessions = emptyList(),
                    recoveryEntries = emptyList(),
                    futsalSessions = emptyList(),
                    progressionRecommendations = emptyList(),
                ) as DomainResult.Success
            ).value,
        )

    private suspend fun exerciseNames(): List<String> = exerciseRepository.findAll().map { it.id.value }

    private suspend fun exerciseNamesInCopy(): List<String> =
        (backupRepository.parseSnapshot(checkNotNull(snapshotStore.load()).json) as DomainResult.Success)
            .value.exercises
            .map { it.id.value }

    /** One damaged session row, as in production: every read that reaches it throws. */
    private class UnreadableWorkoutRepository(
        delegate: WorkoutRepository,
    ) : WorkoutRepository by delegate {
        override fun observeActiveSession(): Flow<WorkoutSession?> = flow { throw IllegalStateException("session row unreadable") }

        override suspend fun findAllForBackup(): List<WorkoutSession> = error("session row unreadable")
    }
}
