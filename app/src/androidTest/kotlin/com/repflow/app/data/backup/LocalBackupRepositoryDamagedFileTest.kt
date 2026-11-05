package com.repflow.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.PreRestoreCopy
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A backup file is untrusted text: a value in it that the domain or a mapper rejects must come back
 * as [BackupRestoreError.InvalidBackup], never close the app, and must leave the stored data as it
 * was. Every damaged file here is [backupFile] with one value changed, and the control tests restore
 * that same file undamaged, so each failure is the damage and not the fixture.
 */
@RunWith(AndroidJUnit4::class)
class LocalBackupRepositoryDamagedFileTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var restoreBackup: RestoreBackup

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        restoreBackup =
            RestoreBackup(
                LocalBackupRepository(database, LocalTrainingDataRepository(database)),
                PreRestoreCopy { restore -> restore() },
            )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun rejectsAZeroLengthRestTimer() = assertRejectedWithStoredDataUntouched(backupFile(restTimerTotalDurationSeconds = 0))

    @Test
    fun rejectsABlankWorkoutId() = assertRejectedWithStoredDataUntouched(backupFile(workoutId = ""))

    @Test
    fun rejectsAMalformedFutsalDate() = assertRejectedWithStoredDataUntouched(backupFile(futsalDate = "not-a-date"))

    @Test
    fun rejectsAnUnknownProgressionResult() = assertRejectedWithStoredDataUntouched(backupFile(progressionResult = "INCREASE_LOAD"))

    @Test
    fun rejectsAnUnknownOverrideResult() =
        assertRejectedWithStoredDataUntouched(backupFile(overrideResult = "\"INCREASE_LOAD\"", overrideAt = "2"))

    @Test
    fun restoresTheSameFileUndamaged() =
        runBlocking<Unit> {
            database.exerciseDao().insert(existingExercise)

            val result = restoreBackup(backupFile())

            assertTrue("Expected success but was $result", result is DomainResult.Success)
            assertEquals(listOf("ex-1"), database.exerciseDao().findAll().map { it.id })
            val session = database.workoutSessionDao().findAll().single()
            assertEquals("session-1", session.id)
            assertEquals(90, session.restTimerTotalDurationSeconds)
            assertEquals(listOf("we-1"), database.workoutExerciseDao().findAllForSession("session-1").map { it.id })
            assertEquals(listOf("set-1"), database.workoutSetDao().findAllForExercise("we-1").map { it.id })
            assertEquals(
                "2026-01-05",
                database
                    .futsalSessionDao()
                    .findAll()
                    .single()
                    .entryDate,
            )
            assertEquals(
                "increase_load",
                database
                    .progressionRecommendationDao()
                    .findAll()
                    .single()
                    .result,
            )
        }

    @Test
    fun restoresTheSameFileWithAValidOverride() =
        runBlocking<Unit> {
            val result = restoreBackup(backupFile(overrideResult = "\"maintain_load\"", overrideAt = "2"))

            assertTrue("Expected success but was $result", result is DomainResult.Success)
            val recommendation = database.progressionRecommendationDao().findAll().single()
            assertEquals("maintain_load", recommendation.overrideResult)
            assertEquals(2L, recommendation.overrideAt)
        }

    private fun assertRejectedWithStoredDataUntouched(damagedFile: String) =
        runBlocking<Unit> {
            database.exerciseDao().insert(existingExercise)

            val error = (restoreBackup(damagedFile) as DomainResult.Failure).error

            assertTrue(
                "Expected InvalidBackup(Malformed) but was $error",
                error is BackupRestoreError.InvalidBackup && error.reason is BackupValidationError.Malformed,
            )
            assertEquals(listOf(existingExercise.id), database.exerciseDao().findAll().map { it.id })
            assertEquals(emptyList<Any>(), database.workoutSessionDao().findAll())
            assertEquals(emptyList<Any>(), database.futsalSessionDao().findAll())
            assertEquals(emptyList<Any>(), database.progressionRecommendationDao().findAll())
        }

    private val existingExercise =
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
        )

    /** A complete, valid file by default; a test passes only the one value it damages. */
    private fun backupFile(
        workoutId: String = "session-1",
        restTimerTotalDurationSeconds: Int = 90,
        futsalDate: String = "2026-01-05",
        progressionResult: String = "increase_load",
        overrideResult: String = "null",
        overrideAt: String = "null",
    ) = """
        {
          "schemaVersion": 2,
          "exercises": [
            {
              "id": "ex-1", "name": "Bench Press", "nameKey": "bench press",
              "trackingType": "WEIGHT_AND_REPS", "instructions": null,
              "defaultLoadIncrementGrams": null, "defaultRestSeconds": null,
              "origin": "BUILT_IN", "archivedAt": null, "createdAt": 1, "updatedAt": 1
            }
          ],
          "trainingPlans": [],
          "trainingPlanVersions": [],
          "plannedExercises": [],
          "workoutSessions": [
            {
              "id": "$workoutId", "trainingPlanVersionId": null, "status": "ACTIVE",
              "startedAt": 10, "endedAt": null, "restTimerEndAtEpochMs": 2000,
              "restTimerTotalDurationSeconds": $restTimerTotalDurationSeconds, "invalidatedAt": null
            }
          ],
          "workoutExercises": [
            {
              "id": "we-1", "sessionId": "$workoutId", "exerciseId": "ex-1", "sortOrder": 0,
              "exerciseNameSnapshot": "Bench Press", "trackingType": "WEIGHT_AND_REPS",
              "plannedExerciseId": null
            }
          ],
          "workoutSets": [
            {
              "id": "set-1", "workoutExerciseId": "we-1", "sortOrder": 0, "load": 60.0,
              "reps": 8, "durationSeconds": null, "rpe": 7.5, "isWarmup": false,
              "createdAt": 11, "updatedAt": 12, "pain": null, "techniqueQuality": null
            }
          ],
          "recoveryEntries": [],
          "futsalSessions": [
            {
              "id": "futsal-1", "entryDate": "$futsalDate", "durationMinutes": 60,
              "sessionRpe": 7.0, "createdAt": 1, "updatedAt": 1
            }
          ],
          "progressionRecommendations": [
            {
              "id": "rec-1", "exerciseId": "ex-1", "result": "$progressionResult",
              "reasons": "hit top of range", "policyVersion": 1, "computedAt": 1,
              "overrideResult": $overrideResult, "overrideAt": $overrideAt
            }
          ]
        }
        """.trimIndent()
}
