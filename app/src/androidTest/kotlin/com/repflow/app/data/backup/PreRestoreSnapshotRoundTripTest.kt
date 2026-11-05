package com.repflow.app.data.backup

import android.content.Context
import androidx.room.Room
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
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The safety copy end to end: the real file store, the real JSON mapper and a real Room transaction, so
 * a copy here is a file an ordinary restore can read, and a failed restore really rolls back.
 */
@RunWith(AndroidJUnit4::class)
class PreRestoreSnapshotRoundTripTest {
    private val directory = File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "pre-restore-round-trip-test")
    private val store = FilePreRestoreSnapshotStore(directory, Dispatchers.IO)
    private val lock = PreRestoreCopyLock()
    private val oldFile = File(directory, "pre-restore-snapshot.json")

    private lateinit var database: RepFlowDatabase
    private lateinit var trainingData: LocalTrainingDataRepository
    private lateinit var backup: LocalBackupRepository
    private lateinit var exercises: LocalExerciseRepository
    private lateinit var restoreBackup: RestoreBackup

    @Before
    fun setUp() {
        directory.deleteRecursively()
        directory.mkdirs()
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        trainingData = LocalTrainingDataRepository(database)
        backup = LocalBackupRepository(database, trainingData)
        exercises = LocalExerciseRepository(database.exerciseDao())
        restoreBackup = restoreThrough(backup)
    }

    @After
    fun tearDown() {
        database.close()
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
                    workoutRepository =
                        LocalWorkoutRepository(
                            database,
                            database.workoutSessionDao(),
                            database.workoutExerciseDao(),
                            database.workoutSetDao(),
                        ),
                    recoveryRepository = LocalRecoveryRepository(database.recoveryEntryDao()),
                    futsalRepository = LocalFutsalRepository(database.futsalSessionDao()),
                    progressionRepository = LocalProgressionRecommendationRepository(database.progressionRecommendationDao()),
                    backupRepository = backup,
                ),
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

    private fun fileHolding(name: String): String =
        backup.serializeSnapshot(
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
