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
 * invariant depends on [androidx.room.RoomDatabase.clearAllTables] behaving
 * correctly when called from *inside* an outer [androidx.room.withTransaction]
 * block rather than as its own transaction - this was flagged as unverified
 * after CP3 and is exactly what this test confirms.
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
        repository = LocalBackupRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun exercise(id: String) =
        (
            Exercise.create(
                id = ExerciseId(id),
                name = (ExerciseName.create("Bench Press") as DomainResult.Success).value,
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

    @Test
    fun replaceAll_rollsBackClearAllTablesWhenAMidTransactionInsertFails() =
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
                "clearAllTables() must roll back with the rest of the transaction on failure",
                listOf("existing-1"),
                database.exerciseDao().findAll().map { it.id },
            )
        }
}
