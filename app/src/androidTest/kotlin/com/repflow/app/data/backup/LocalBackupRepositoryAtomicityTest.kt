package com.repflow.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Real-SQLite instrumented coverage for [LocalBackupRepository.replaceAll]'s
 * atomicity, run against an in-memory Room database on an actual
 * device/emulator (JVM-only Robolectric is deliberately not used here, per
 * the pattern in [com.repflow.app.infrastructure.database.exercise.ExerciseDaoTest]).
 *
 * The milestone 7 reference's "restore replaces all local data atomically"
 * invariant - since remediation-1 CP14, all local **training** data - depends
 * on the restore's clear rolling back with the rest of the transaction. The
 * clear is no longer `clearAllTables()` (which would also have deleted the
 * `settings` row, which no backup carries) but
 * [LocalTrainingDataRepository.clearTrainingData], a repository method issuing
 * its own deletes in its own [androidx.room.withTransaction] - called from
 * *inside* [LocalBackupRepository.replaceAll]'s outer one. Whether that inner
 * transaction joins the outer one, and rolls back with it when a later insert
 * fails, is the same nested-transaction question this file was written to
 * answer for `clearAllTables()`, asked of the new call; the third test answers
 * it.
 */
@RunWith(AndroidJUnit4::class)
class LocalBackupRepositoryAtomicityTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalBackupRepository

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        repository = LocalBackupRepository(database, LocalTrainingDataRepository(database))
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun exercise(
        id: String,
        name: String = "Bench Press $id",
    ) = (
        Exercise.create(
            id = ExerciseId(id),
            name = (ExerciseName.create(name) as DomainResult.Success).value,
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            instructions = null,
            defaultLoadIncrement = null,
            defaultRestDuration = null,
            origin = ExerciseOrigin.BUILT_IN,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        ) as DomainResult.Success
    ).value

    @Test
    fun replaceAll_persistsEveryRowOnSuccess() =
        runBlocking {
            val snapshot =
                (
                    BackupSnapshot.create(
                        schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                        exercises = listOf(exercise("ex-1")),
                        trainingPlans = emptyList(),
                        workoutSessions = emptyList(),
                        recoveryEntries = emptyList(),
                        futsalSessions = emptyList(),
                        progressionRecommendations = emptyList(),
                    ) as DomainResult.Success
                ).value

            val result = repository.replaceAll(snapshot)

            assertTrue(result is DomainResult.Success)
            assertEquals(listOf("ex-1"), database.exerciseDao().findAll().map { it.id })
        }

    /**
     * Manual-smoke-test surrogate for the milestone 7 DoD's "export -> wipe
     * -> restore -> data intact" workflow: exercises the full
     * serialize -> parse -> replaceAll pipeline through real JSON text and a
     * real Room database, exactly as [com.repflow.app.application.backup.ExportBackup]
     * and [com.repflow.app.application.backup.RestoreBackup] would.
     */
    @Test
    fun exportThenRestoreRoundTripsThroughRealJsonAndARealDatabase() =
        runBlocking {
            // Simulates data already on-device before a restore replaces it.
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = "old-1",
                    name = "Deadlift",
                    nameKey = "deadlift",
                    trackingType = "WEIGHT_AND_REPS",
                    instructions = null,
                    defaultLoadIncrementGrams = null,
                    defaultRestSeconds = null,
                    origin = "BUILT_IN",
                    archivedAt = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )

            val original =
                (
                    BackupSnapshot.create(
                        schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                        exercises = listOf(exercise("ex-1"), exercise("ex-2")),
                        trainingPlans = emptyList(),
                        workoutSessions = emptyList(),
                        recoveryEntries = emptyList(),
                        futsalSessions = emptyList(),
                        progressionRecommendations = emptyList(),
                    ) as DomainResult.Success
                ).value

            val json = repository.serializeSnapshot(original)
            val parsed = (repository.parseSnapshot(json) as DomainResult.Success).value
            val restoreResult = repository.replaceAll(parsed)

            check(restoreResult is DomainResult.Success) { "replaceAll failed: $restoreResult" }
            assertEquals(
                listOf("ex-1", "ex-2"),
                database
                    .exerciseDao()
                    .findAll()
                    .map { it.id }
                    .sorted(),
            )
        }

    @Test
    fun replaceAll_rollsBackTheScopedTrainingDataClearWhenAMidTransactionInsertFails() =
        runBlocking {
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = "existing-1",
                    name = "Squat",
                    nameKey = "squat",
                    trackingType = "WEIGHT_AND_REPS",
                    instructions = null,
                    defaultLoadIncrementGrams = null,
                    defaultRestSeconds = null,
                    origin = "BUILT_IN",
                    archivedAt = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )

            // Two exercises sharing the same id violate the primary key, forcing
            // the default OnConflictStrategy.ABORT insert to throw partway
            // through replaceAll's transaction.
            val snapshot =
                (
                    BackupSnapshot.create(
                        schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                        exercises = listOf(exercise("dup"), exercise("dup")),
                        trainingPlans = emptyList(),
                        workoutSessions = emptyList(),
                        recoveryEntries = emptyList(),
                        futsalSessions = emptyList(),
                        progressionRecommendations = emptyList(),
                    ) as DomainResult.Success
                ).value

            val result = repository.replaceAll(snapshot)

            assertTrue(result is DomainResult.Failure)
            assertEquals(
                "clearTrainingData() must roll back with the rest of the transaction on failure",
                listOf("existing-1"),
                database.exerciseDao().findAll().map { it.id },
            )
        }

    /**
     * Distinct from the primary-key conflict above (Milestone 8, CP14): two
     * exercises with different ids but the same normalized name violate the
     * `name_key` unique index instead, a different `SQLiteConstraintException`
     * path than a duplicate primary key.
     */
    @Test
    fun replaceAll_rollsBackOnADistinctIdSameNameKeyConflict() =
        runBlocking {
            database.exerciseDao().insert(
                ExerciseEntity(
                    id = "existing-1",
                    name = "Squat",
                    nameKey = "squat",
                    trackingType = "WEIGHT_AND_REPS",
                    instructions = null,
                    defaultLoadIncrementGrams = null,
                    defaultRestSeconds = null,
                    origin = "BUILT_IN",
                    archivedAt = null,
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            )

            val snapshot =
                (
                    BackupSnapshot.create(
                        schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                        exercises = listOf(exercise("a", name = "Bench Press"), exercise("b", name = "Bench Press")),
                        trainingPlans = emptyList(),
                        workoutSessions = emptyList(),
                        recoveryEntries = emptyList(),
                        futsalSessions = emptyList(),
                        progressionRecommendations = emptyList(),
                    ) as DomainResult.Success
                ).value

            val result = repository.replaceAll(snapshot)

            assertTrue(result is DomainResult.Failure)
            assertEquals(
                "a name_key conflict must roll back exactly like a primary-key conflict",
                listOf("existing-1"),
                database.exerciseDao().findAll().map { it.id },
            )
        }
}
