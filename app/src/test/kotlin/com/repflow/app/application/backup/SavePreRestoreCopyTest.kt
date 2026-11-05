package com.repflow.app.application.backup

import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SavePreRestoreCopyTest {
    private val exerciseRepository = InMemoryExerciseRepository()
    private val workoutRepository = InMemoryWorkoutRepository()
    private val backupRepository = InMemoryBackupRepository()
    private val trainingData = InMemoryTrainingDataRepository()
    private val store = InMemoryPreRestoreSnapshotStore()
    private val lock = PreRestoreCopyLock()
    private val oldCopy = PreRestoreSnapshot("the old copy's exact text", Instant.parse("2026-01-01T00:00:00Z"))

    private fun exportBackup(workouts: WorkoutRepository = workoutRepository) =
        ExportBackup(
            exerciseRepository = exerciseRepository,
            trainingPlanRepository = InMemoryTrainingPlanRepository(),
            workoutRepository = workouts,
            recoveryRepository = InMemoryRecoveryRepository(),
            futsalRepository = InMemoryFutsalRepository(),
            progressionRepository = InMemoryProgressionRecommendationRepository(),
            backupRepository = backupRepository,
        )

    private val copy = SavePreRestoreCopy(trainingData, exportBackup(), backupRepository, store, lock)

    private val succeed: suspend () -> DomainResult<Unit, BackupRestoreError> = { DomainResult.Success(Unit) }
    private val fail: suspend () -> DomainResult<Unit, BackupRestoreError> = { DomainResult.Failure(BackupRestoreError.Unavailable) }

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

    /** The exercise names inside a saved copy: the fake's text is a token for the snapshot it serialized. */
    private fun exerciseNamesIn(json: String?): List<String> =
        (backupRepository.parseSnapshot(checkNotNull(json)) as DomainResult.Success).value.exercises.map { it.id.value }

    @Test
    fun `the copy is ExportBackup's output, saved before the restore runs`() =
        runTest {
            holdExercise("Squat")
            var savedAtRestore: String? = null

            val result =
                copy.aroundRestore {
                    savedAtRestore = store.new?.json
                    DomainResult.Success(Unit)
                }

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(listOf("Squat"), exerciseNamesIn(savedAtRestore))
            assertEquals(
                (backupRepository.parseSnapshot(exportBackup()()) as DomainResult.Success).value,
                (backupRepository.parseSnapshot(checkNotNull(savedAtRestore)) as DomainResult.Success).value,
            )
        }

    @Test
    fun `the read, the save and the restore are one transaction, and the promote comes after its commit`() =
        runTest {
            holdExercise("Squat")
            val transaction = RecordingTransaction()

            copyThrough(transaction).aroundRestore {
                store.calls += "restore"
                DomainResult.Success(Unit)
            }

            assertEquals(0, transaction.readsBeforeItBegan)
            assertEquals(1, trainingData.hasTrainingDataReads)
            assertEquals(listOf("begin", "saveNew", "restore", "commit", "promoteNew"), store.calls)
        }

    @Test
    fun `a returned Failure discards the new copy after the transaction has ended`() =
        runTest {
            holdExercise("Squat")

            copyThrough(RecordingTransaction()).aroundRestore(fail)

            assertEquals(listOf("begin", "saveNew", "commit", "discardNew"), store.calls)
        }

    @Test
    fun `a commit that fails returns Unavailable, discards the new copy and leaves the old one byte-identical`() =
        runTest {
            store.old = oldCopy
            holdExercise("Squat")

            val result = copyThrough(RecordingTransaction(commitFails = true)).aroundRestore(succeed)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertEquals(listOf("begin", "saveNew", "commit", "discardNew"), store.calls)
            assertNull(store.new)
            assertEquals(oldCopy, store.old)
        }

    @Test
    fun `a restore with no training data still runs in the transaction, and a leftover new copy stays`() =
        runTest {
            trainingData.hasTrainingData = false
            store.new = oldCopy

            copyThrough(RecordingTransaction()).aroundRestore(succeed)

            assertEquals(listOf("begin", "commit"), store.calls)
            assertEquals(oldCopy, store.new)
        }

    @Test
    fun `a returned Success promotes the new copy over the old one`() =
        runTest {
            store.old = oldCopy
            holdExercise("Squat")

            copy.aroundRestore(succeed)

            assertNull(store.new)
            assertEquals(listOf("Squat"), exerciseNamesIn(store.old?.json))
            assertEquals(listOf("saveNew", "promoteNew"), store.calls)
        }

    @Test
    fun `a returned Failure discards the new copy and the old one is byte-identical`() =
        runTest {
            store.old = oldCopy
            holdExercise("Squat")

            val result = copy.aroundRestore(fail)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertNull(store.new)
            assertEquals(oldCopy, store.old)
            assertEquals(listOf("saveNew", "discardNew"), store.calls)
        }

    @Test
    fun `a thrown CancellationException is rethrown, discards nothing, and the new copy wins at the next load`() =
        runTest {
            store.old = oldCopy
            holdExercise("Squat")

            val thrown =
                runCatching { copy.aroundRestore { throw CancellationException("resumed after its work finished") } }
                    .exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertEquals(listOf("saveNew"), store.calls)
            assertEquals(listOf("Squat"), exerciseNamesIn(store.load()?.json))
        }

    @Test
    fun `a failing save returns Unavailable and does not restore`() =
        runTest {
            store.nextSaveFails = true
            var restored = false

            val result =
                copy.aroundRestore {
                    restored = true
                    DomainResult.Success(Unit)
                }

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertFalse(restored)
            assertEquals("A leftover new copy is not discarded", listOf("saveNew"), store.calls)
        }

    @Test
    fun `a hasTrainingData read that throws returns Unavailable, does not restore and calls no store write`() =
        runTest {
            trainingData.readFailure = IllegalStateException("query failed")
            var restored = false

            val result =
                copy.aroundRestore {
                    restored = true
                    DomainResult.Success(Unit)
                }

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertFalse(restored)
            assertEquals(emptyList<String>(), store.calls)
        }

    @Test
    fun `an export that throws returns Unavailable, does not restore and calls no store write`() =
        runTest {
            val damaged = damagedRowCopy(IllegalStateException("session row unreadable"))
            var restored = false

            val result =
                damaged.aroundRestore {
                    restored = true
                    DomainResult.Success(Unit)
                }

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), result)
            assertFalse(restored)
            assertEquals(emptyList<String>(), store.calls)
        }

    @Test
    fun `a CancellationException from the hasTrainingData read is rethrown`() =
        runTest {
            trainingData.readFailure = CancellationException("cancelled in the read")

            val thrown = runCatching { copy.aroundRestore(succeed) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertEquals(emptyList<String>(), store.calls)
        }

    @Test
    fun `a CancellationException from the export is rethrown`() =
        runTest {
            val damaged = damagedRowCopy(CancellationException("cancelled in the export"))

            val thrown = runCatching { damaged.aroundRestore(succeed) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertEquals(emptyList<String>(), store.calls)
        }

    @Test
    fun `with no training data the restore runs, nothing is written and the old copy stays`() =
        runTest {
            store.old = oldCopy
            trainingData.hasTrainingData = false

            val result = copy.aroundRestore(succeed)

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(emptyList<String>(), store.calls)
            assertEquals(oldCopy, store.old)
        }

    @Test
    fun `the second of two overlapping restores reads nothing until the first has finished, and its copy holds what the first left`() =
        runTest {
            holdExercise("A")
            val firstHolds = holdOverlappingRestores { DomainResult.Success(Unit) }

            firstHolds.releaseRead()
            assertEquals("The first has read and saved, and the second has not read", 1, trainingData.hasTrainingDataReads)
            assertEquals(listOf("saveNew"), store.calls)

            holdExercise("B")
            firstHolds.finishRestore()

            assertEquals(2, trainingData.hasTrainingDataReads)
            assertEquals(listOf("saveNew", "promoteNew", "saveNew", "promoteNew"), store.calls)
            assertEquals(listOf("B"), exerciseNamesIn(store.load()?.json))
        }

    @Test
    fun `when the first of two overlapping restores fails, the second still runs against the unchanged data`() =
        runTest {
            holdExercise("A")
            store.old = oldCopy
            val firstHolds = holdOverlappingRestores { DomainResult.Failure(BackupRestoreError.Unavailable) }

            firstHolds.releaseRead()
            firstHolds.finishRestore()

            assertEquals(listOf("saveNew", "discardNew", "saveNew", "promoteNew"), store.calls)
            assertEquals(listOf("A"), exerciseNamesIn(store.load()?.json))
            assertTrue("The second restore ran", firstHolds.secondRestored())
        }

    private fun copyThrough(transaction: BackupRepository) = SavePreRestoreCopy(trainingData, exportBackup(), transaction, store, lock)

    /** Puts the transaction's two ends in the store's list of calls, and can make its commit fail. */
    private inner class RecordingTransaction(
        private val commitFails: Boolean = false,
    ) : BackupRepository by backupRepository {
        var readsBeforeItBegan = -1
            private set

        override suspend fun inRestoreTransaction(
            block: suspend () -> DomainResult<Unit, BackupRestoreError>,
        ): DomainResult<Unit, BackupRestoreError> {
            readsBeforeItBegan = trainingData.hasTrainingDataReads
            store.calls += "begin"
            val outcome = block()
            store.calls += "commit"
            return if (commitFails) DomainResult.Failure(BackupRestoreError.Unavailable) else outcome
        }
    }

    private fun damagedRowCopy(failure: Throwable) =
        SavePreRestoreCopy(
            trainingData,
            exportBackup(
                object : WorkoutRepository by workoutRepository {
                    override suspend fun findAllForBackup(): List<WorkoutSession> = throw failure
                },
            ),
            backupRepository,
            store,
            lock,
        )

    private class OverlappingRestores(
        val releaseRead: () -> Unit,
        val finishRestore: () -> Unit,
        val secondRestored: () -> Boolean,
    )

    /**
     * Starts a first restore, suspended in its read and then in `restore()`, and a second one behind it
     * on the same lock and store. Until the first is released, neither the read count nor the store's
     * calls may move for the second.
     */
    private fun TestScope.holdOverlappingRestores(firstOutcome: () -> DomainResult<Unit, BackupRestoreError>): OverlappingRestores {
        val readGate = CompletableDeferred<Unit>()
        val restoreGate = CompletableDeferred<Unit>()
        var secondRestored = false
        trainingData.readGate = readGate
        val eager = UnconfinedTestDispatcher(testScheduler)
        launch(eager) {
            copy.aroundRestore {
                restoreGate.await()
                firstOutcome()
            }
        }
        launch(eager) {
            copy.aroundRestore {
                secondRestored = true
                DomainResult.Success(Unit)
            }
        }
        assertEquals("Only the first has started its read", 1, trainingData.hasTrainingDataReads)
        assertEquals(emptyList<String>(), store.calls)
        return OverlappingRestores(
            releaseRead = { readGate.complete(Unit) },
            finishRestore = {
                restoreGate.complete(Unit)
                testScheduler.advanceUntilIdle()
            },
            secondRestored = { secondRestored },
        )
    }
}
