package com.repflow.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.EraseAllData
import com.repflow.app.application.backup.ExportBackup
import com.repflow.app.application.backup.PreRestoreCopyLock
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.backup.SavePreRestoreCopy
import com.repflow.app.application.backup.TrainingDataError
import com.repflow.app.application.backup.TrainingDataRepository
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.WorkoutOperationError
import com.repflow.app.application.workout.WorkoutPersistenceError
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.progression.LocalProgressionRecommendationRepository
import com.repflow.app.data.recovery.LocalFutsalRepository
import com.repflow.app.data.recovery.LocalRecoveryRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.di.DatabaseModule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * The safety copy end to end: the real file store, the real JSON mapper and a real Room transaction, so
 * a copy here is a file an ordinary restore can read, and a failed restore really rolls back.
 *
 * The database is a real file in the app's real journal mode. An in-memory one has a single connection,
 * so a read made during a restore would wait for it, which no phone does.
 */
@RunWith(AndroidJUnit4::class)
class PreRestoreSnapshotRoundTripTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "pre-restore-round-trip-test")
    private val store = FilePreRestoreSnapshotStore(directory, Dispatchers.IO)
    private val lock = PreRestoreCopyLock()
    private val oldFile = File(directory, "pre-restore-snapshot.json")

    private lateinit var database: RepFlowDatabase
    private lateinit var trainingData: LocalTrainingDataRepository
    private lateinit var backup: LocalBackupRepository
    private lateinit var exercises: LocalExerciseRepository
    private lateinit var workouts: LocalWorkoutRepository
    private lateinit var startWorkout: StartWorkoutSession
    private lateinit var restoreBackup: RestoreBackup

    @Before
    fun setUp() {
        directory.deleteRecursively()
        directory.mkdirs()
        context.deleteDatabase(DATABASE_NAME)
        database = DatabaseModule.buildRepFlowDatabase(context, DATABASE_NAME)
        assertTrue(database.openHelper.writableDatabase.isWriteAheadLoggingEnabled)
        trainingData = LocalTrainingDataRepository(database)
        backup = LocalBackupRepository(database, trainingData)
        exercises = LocalExerciseRepository(database.exerciseDao())
        workouts =
            LocalWorkoutRepository(
                database,
                database.workoutSessionDao(),
                database.workoutExerciseDao(),
                database.workoutSetDao(),
            )
        startWorkout = StartWorkoutSession(workouts, { Instant.now() }, { UUID.randomUUID().toString() })
        restoreBackup = restoreThrough(backup)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
        directory.deleteRecursively()
    }

    @Test
    fun aRestoreKeepsACopyThatRestoresTheDataItReplaced() =
        runBlocking<Unit> {
            seed("A")

            assertSucceeded(restoreBackup(fileHolding("B")))

            assertEquals(listOf("B"), exerciseNames())
            assertEquals(listOf("A"), exerciseNamesIn(copyText()))

            assertSucceeded(restoreBackup(copyText()))

            assertEquals("Restoring the copy brings the data back", listOf("A"), exerciseNames())
            assertEquals("The two states swap", listOf("B"), exerciseNamesIn(copyText()))
        }

    @Test
    fun aFailedRestoreOfAFileLeavesTheOldCopyByteIdentical() =
        runBlocking<Unit> {
            seed("A")
            assertSucceeded(restoreBackup(fileHolding("B")))
            val before = oldFile.readBytes()

            val result = restoreThrough(LocalBackupRepository(database, FailingClear(trainingData)))(fileHolding("C"))

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertArrayEquals(before, oldFile.readBytes())
            assertEquals(listOf("A"), exerciseNamesIn(copyText()))
            assertEquals("The transaction rolled back", listOf("B"), exerciseNames())
        }

    @Test
    fun aFailedRestoreOfTheCopyLeavesTheOldCopyByteIdentical() =
        runBlocking<Unit> {
            seed("A")
            assertSucceeded(restoreBackup(fileHolding("B")))
            val before = oldFile.readBytes()

            val result = restoreThrough(LocalBackupRepository(database, FailingClear(trainingData)))(copyText())

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertArrayEquals(before, oldFile.readBytes())
            assertEquals("The copy can still be restored", listOf("A"), exerciseNamesIn(copyText()))
            assertEquals(listOf("B"), exerciseNames())
        }

    /** As `BackupViewModel` runs it: the caller's scope is cancelled, and the restore still commits and promotes. */
    @Test
    fun theWholePathRunsUnderNonCancellableWithARealRoomTransaction() =
        runBlocking<Unit> {
            seed("A")
            val inFile = fileHolding("B")
            val held = CompletableDeferred<Unit>()
            val gated = restoreThrough(GatedBackupRepository(backup, CompletableDeferred(Unit), held))

            val restoring = launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { gated(inFile) } }
            restoring.cancel()
            restoring.join()

            assertEquals(listOf("B"), exerciseNames())
            assertEquals(listOf("A"), exerciseNamesIn(copyText()))
            assertTrue(held.isCompleted)
        }

    @Test
    fun twoOverlappingRestoresEndWithTheLastFileAndACopyOfWhatTheFirstLeft() =
        runBlocking<Unit> {
            seed("A")
            val gate = CompletableDeferred<Unit>()
            val atGate = CompletableDeferred<Unit>()
            val first = restoreThrough(GatedBackupRepository(backup, gate, atGate))
            val inFileB = fileHolding("B")
            val inFileC = fileHolding("C")

            val firstResult = async { first(inFileB) }
            atGate.await()
            val secondResult = async(start = CoroutineStart.UNDISPATCHED) { restoreBackup(inFileC) }
            assertFalse(secondResult.isCompleted)
            assertEquals("The first is held before its replaceAll", listOf("A"), exerciseNames())

            gate.complete(Unit)

            assertSucceeded(firstResult.await())
            assertSucceeded(secondResult.await())
            assertEquals(listOf("C"), exerciseNames())
            assertEquals(listOf("B"), exerciseNamesIn(copyText()))

            assertSucceeded(restoreBackup(copyText()))
            assertEquals(listOf("B"), exerciseNames())
        }

    @Test
    fun anEraseStartedDuringAHeldRestoreEndsWithNoDataAndNoCopy() =
        runBlocking<Unit> {
            seed("A")
            val gate = CompletableDeferred<Unit>()
            val atGate = CompletableDeferred<Unit>()
            val held = restoreThrough(GatedBackupRepository(backup, gate, atGate))
            val inFile = fileHolding("B")

            val restoring = async { held(inFile) }
            atGate.await()
            val erasing = async(start = CoroutineStart.UNDISPATCHED) { EraseAllData(trainingData, store, lock)() }
            assertFalse(erasing.isCompleted)

            gate.complete(Unit)

            assertSucceeded(restoring.await())
            assertEquals(DomainResult.Success(Unit), erasing.await())
            assertFalse(trainingData.hasTrainingData())
            assertNull(store.load())
        }

    /**
     * As the user could: with a restore held after its copy is saved and its screen gone, start a workout
     * from Home. The start waits, the restore finishes, and the workout is there on top of the restored data.
     */
    @Test
    fun aWorkoutStartedDuringARestoreWhoseScreenHasGoneWaitsForItAndSurvivesIt() =
        runBlocking<Unit> {
            seed("A")
            val inFile = fileHolding("B")
            val held = holdRestoreWithItsScreenGone(backup, inFile)

            val starting = async(Dispatchers.Default) { startWorkout(StartWorkoutSessionCommand(null)) }
            val finishedDuringTheRestore = withTimeoutOrNull(WRITE_WAIT_MILLIS) { starting.await() }
            held.gate.complete(Unit)

            assertSucceeded(held.result.await())
            val id = (starting.await() as DomainResult.Success).value
            assertNotNull("The workout the user saw start is still there", workouts.findById(id))
            assertEquals(id, workouts.findActiveSession()?.id)
            assertEquals(listOf("B"), exerciseNames())
            assertEquals(listOf("A"), exerciseNamesIn(copyText()))
            assertNull("The start waited for the restore", finishedDuringTheRestore)
        }

    /** Started during a restore of a file with a running workout, its read can't see that workout yet. Its write can. */
    @Test
    fun aWorkoutStartedDuringARestoreOfARunningWorkoutIsRefusedInsteadOfBecomingASecondOne() =
        runBlocking<Unit> {
            seed("A")
            val running = WorkoutSession.start(WorkoutSessionId("in-the-file"), null, Instant.parse("2026-01-01T00:00:00Z"))
            val held = holdRestoreWithItsScreenGone(backup, fileHolding("B", listOf(running)))

            val starting = async(Dispatchers.Default) { startWorkout(StartWorkoutSessionCommand(null)) }
            withTimeoutOrNull(WRITE_WAIT_MILLIS) { starting.await() }
            held.gate.complete(Unit)

            assertSucceeded(held.result.await())
            assertEquals(DomainResult.Failure(WorkoutOperationError.ActiveSessionAlreadyExists), starting.await())
            assertEquals(
                listOf(running.id),
                workouts.findAllForBackup().filter { it.status == WorkoutSessionStatus.ACTIVE }.map { it.id },
            )
        }

    /** The guard is SQLite's one writer, so it covers every repository, not only the workouts'. */
    @Test
    fun anExerciseAddedDuringARestoreSurvivesIt() =
        runBlocking<Unit> {
            seed("A")
            val held = holdRestoreWithItsScreenGone(backup, fileHolding("B"))

            val adding = async(Dispatchers.Default) { exercises.insert(exercise("C")) }
            val finishedDuringTheRestore = withTimeoutOrNull(WRITE_WAIT_MILLIS) { adding.await() }
            held.gate.complete(Unit)

            assertSucceeded(held.result.await())
            assertTrue(adding.await() is DomainResult.Success)
            assertEquals(setOf("B", "C"), exerciseNames().toSet())
            assertNull("The write waited for the restore", finishedDuringTheRestore)
        }

    @Test
    fun aWorkoutStartedDuringARestoreThatFailsIsKeptAndTheOldCopyStaysByteIdentical() =
        runBlocking<Unit> {
            seed("A")
            assertSucceeded(restoreBackup(fileHolding("B")))
            val before = oldFile.readBytes()
            val failing = LocalBackupRepository(database, FailingClear(trainingData))
            val held = holdRestoreWithItsScreenGone(failing, fileHolding("C"))

            val starting = async(Dispatchers.Default) { startWorkout(StartWorkoutSessionCommand(null)) }
            withTimeoutOrNull(WRITE_WAIT_MILLIS) { starting.await() }
            held.gate.complete(Unit)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), held.result.await())
            val id = (starting.await() as DomainResult.Success).value
            assertNotNull(workouts.findById(id))
            assertEquals(listOf("B"), exerciseNames())
            assertArrayEquals(before, oldFile.readBytes())
        }

    @Test
    fun aWorkoutStartedBeforeARestoreIsInItsCopy() =
        runBlocking<Unit> {
            seed("A")
            val id = (startWorkout(StartWorkoutSessionCommand(null)) as DomainResult.Success).value

            assertSucceeded(restoreBackup(fileHolding("B")))

            assertNull(workouts.findById(id))
            val copied = (backup.parseSnapshot(copyText()) as DomainResult.Success).value
            assertEquals(listOf(id), copied.workoutSessions.map { it.id })
        }

    /** A write that waited for the restore and finds its workout gone says so, and does not bring it back. */
    @Test
    fun aChangeToAWorkoutTheRestoreDeletedFailsInsteadOfBringingItBack() =
        runBlocking<Unit> {
            seed("A")
            val session = WorkoutSession.start(WorkoutSessionId("deleted-by-the-restore"), null, Instant.parse("2026-01-01T00:00:00Z"))
            assertTrue(workouts.insert(session) is DomainResult.Success)
            val held = holdRestoreWithItsScreenGone(backup, fileHolding("B"))

            val changing = async(Dispatchers.Default) { workouts.update(session) }
            withTimeoutOrNull(WRITE_WAIT_MILLIS) { changing.await() }
            held.gate.complete(Unit)

            assertSucceeded(held.result.await())
            assertEquals(DomainResult.Failure(WorkoutPersistenceError.NotFound), changing.await())
            assertNull(workouts.findById(session.id))
        }

    private class HeldRestore(
        val gate: CompletableDeferred<Unit>,
        val result: CompletableDeferred<DomainResult<Unit, BackupRestoreError>>,
    )

    /** As `BackupViewModel` runs it: cancelling the caller's scope is leaving the screen, and the restore goes on to [HeldRestore.gate]. */
    private suspend fun CoroutineScope.holdRestoreWithItsScreenGone(
        repository: BackupRepository,
        inFile: String,
    ): HeldRestore {
        val gate = CompletableDeferred<Unit>()
        val atGate = CompletableDeferred<Unit>()
        val result = CompletableDeferred<DomainResult<Unit, BackupRestoreError>>()
        val restore = restoreThrough(GatedBackupRepository(repository, gate, atGate))
        launch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { result.complete(restore(inFile)) } }.cancel()
        atGate.await()
        return HeldRestore(gate, result)
    }

    private fun restoreThrough(repository: BackupRepository) =
        RestoreBackup(
            repository,
            SavePreRestoreCopy(
                trainingData,
                ExportBackup(
                    exerciseRepository = exercises,
                    trainingPlanRepository =
                        LocalTrainingPlanRepository(
                            database,
                            database.trainingPlanDao(),
                            database.trainingPlanVersionDao(),
                            database.plannedExerciseDao(),
                        ),
                    workoutRepository = workouts,
                    recoveryRepository = LocalRecoveryRepository(database.recoveryEntryDao()),
                    futsalRepository = LocalFutsalRepository(database.futsalSessionDao()),
                    progressionRepository = LocalProgressionRecommendationRepository(database.progressionRecommendationDao()),
                    backupRepository = backup,
                ),
                repository,
                store,
                lock,
            ),
        )

    private fun assertSucceeded(result: DomainResult<Unit, BackupRestoreError>) =
        assertTrue("Expected success but was $result", result is DomainResult.Success)

    private suspend fun seed(name: String) {
        check(exercises.insert(exercise(name)) is DomainResult.Success) { "seeding $name failed" }
    }

    private suspend fun exerciseNames(): List<String> = exercises.findAll().map { it.id.value }

    private suspend fun copyText(): String = checkNotNull(store.load()).json

    private fun exerciseNamesIn(json: String): List<String> =
        (backup.parseSnapshot(json) as DomainResult.Success).value.exercises.map { it.id.value }

    private fun fileHolding(
        name: String,
        sessions: List<WorkoutSession> = emptyList(),
    ): String =
        backup.serializeSnapshot(
            (
                BackupSnapshot.create(
                    schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                    exercises = listOf(exercise(name)),
                    trainingPlans = emptyList(),
                    workoutSessions = sessions,
                    recoveryEntries = emptyList(),
                    futsalSessions = emptyList(),
                    progressionRecommendations = emptyList(),
                ) as DomainResult.Success
            ).value,
        )

    private fun exercise(name: String): Exercise =
        (
            Exercise.create(
                id = ExerciseId(name),
                name = (ExerciseName.create(name) as DomainResult.Success).value,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ) as DomainResult.Success
        ).value

    private companion object {
        const val DATABASE_NAME = "pre-restore-round-trip-test.db"

        /** Far longer than a write with nothing in its way takes. */
        const val WRITE_WAIT_MILLIS = 500L
    }

    /** Inside a real restore's transaction, so this makes the real `replaceAll` roll back and return `Unavailable`. */
    private class FailingClear(
        delegate: TrainingDataRepository,
    ) : TrainingDataRepository by delegate {
        override suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError> =
            DomainResult.Failure(TrainingDataError.Unavailable)
    }

    /** Holds at [gate], just before the real `replaceAll`, and says so through [atGate]. */
    private class GatedBackupRepository(
        private val delegate: BackupRepository,
        private val gate: CompletableDeferred<Unit>,
        private val atGate: CompletableDeferred<Unit>,
    ) : BackupRepository by delegate {
        override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
            atGate.complete(Unit)
            gate.await()
            return delegate.replaceAll(snapshot)
        }
    }
}
