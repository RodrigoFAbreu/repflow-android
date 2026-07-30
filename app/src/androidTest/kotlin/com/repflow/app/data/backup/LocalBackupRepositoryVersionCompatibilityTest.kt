package com.repflow.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage (Milestone 8, CP14) for backup
 * schema-version compatibility through the actual [LocalBackupRepository]
 * and [RestoreBackup] - not just [BackupSnapshot.create] in isolation, which
 * only exercises the domain-level version check and can't prove a real
 * pre-Milestone-8 (schema version 1) backup file still restores correctly
 * end to end, or that a too-new file is rejected before touching the
 * database.
 */
@RunWith(AndroidJUnit4::class)
class LocalBackupRepositoryVersionCompatibilityTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalBackupRepository
    private lateinit var restoreBackup: RestoreBackup

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        repository = LocalBackupRepository(database)
        restoreBackup = RestoreBackup(repository)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    // Exactly what a real backup exported by a pre-Milestone-8 build would contain -
    // none of the five fields CP9-CP14 added exist as keys at all.
    private val v1ShapedJson =
        """
        {
          "schemaVersion": 1,
          "exercises": [
            {
              "id": "ex-1", "name": "Bench Press", "nameKey": "bench press",
              "trackingType": "WEIGHT_AND_REPS", "instructions": null,
              "defaultLoadIncrementGrams": null, "defaultRestSeconds": null,
              "origin": "BUILT_IN", "createdAt": 1, "updatedAt": 1
            }
          ],
          "trainingPlans": [
            {"id": "plan-1", "name": "Push Day", "nameKey": "push day", "createdAt": 1, "updatedAt": 1}
          ],
          "trainingPlanVersions": [
            {"id": "v-1", "planId": "plan-1", "versionNumber": 1, "note": null, "createdAt": 1}
          ],
          "plannedExercises": [
            {
              "id": "pe-1", "versionId": "v-1", "exerciseId": "ex-1", "sortOrder": 0,
              "targetSets": 3, "targetKind": "REPS", "repMin": 8, "repMax": 12,
              "durationMinSeconds": null, "durationMaxSeconds": null, "restSeconds": 90,
              "isOptional": false
            }
          ],
          "workoutSessions": [
            {
              "id": "session-1", "trainingPlanVersionId": "v-1", "status": "COMPLETED",
              "startedAt": 10, "endedAt": 20, "restTimerEndAtEpochMs": null,
              "restTimerTotalDurationSeconds": null
            }
          ],
          "workoutExercises": [
            {
              "id": "we-1", "sessionId": "session-1", "exerciseId": "ex-1", "sortOrder": 0,
              "exerciseNameSnapshot": "Bench Press", "trackingType": "WEIGHT_AND_REPS",
              "plannedExerciseId": "pe-1"
            }
          ],
          "workoutSets": [
            {
              "id": "set-1", "workoutExerciseId": "we-1", "sortOrder": 0, "load": 60.0,
              "reps": 8, "durationSeconds": null, "rpe": 7.5, "isWarmup": false,
              "createdAt": 11, "updatedAt": 12
            }
          ],
          "recoveryEntries": [],
          "futsalSessions": [],
          "progressionRecommendations": []
        }
        """.trimIndent()

    @Test
    fun restoresAV1ShapedBackupWithTheFiveNewColumnsComingBackNull() =
        runBlocking {
            val result = restoreBackup(v1ShapedJson)

            assertTrue(result is DomainResult.Success)
            val plan = database.trainingPlanDao().findById("plan-1")
            assertEquals(null, plan?.archivedAt)
            val plannedExercise = database.plannedExerciseDao().findAllForVersion("v-1").single()
            assertEquals(null, plannedExercise.targetWarmupSets)
            val session = database.workoutSessionDao().findById("session-1")
            assertEquals(null, session?.invalidatedAt)
            val set = database.workoutSetDao().findAllForExercise("we-1").single()
            assertEquals(null, set.pain)
            assertEquals(null, set.techniqueQuality)
            // Everything else survived the restore too.
            assertEquals("Push Day", plan?.name)
            assertEquals("COMPLETED", session?.status)
            assertEquals(60.0, set.load)
        }

    @Test
    fun rejectsABackupNewerThanThisAppUnderstandsWithoutTouchingStoredData() =
        runBlocking {
            database.exerciseDao().insert(
                com.repflow.app.infrastructure.database.exercise.ExerciseEntity(
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
            val futureVersionJson = v1ShapedJson.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 99")

            val result = restoreBackup(futureVersionJson)

            assertEquals(
                BackupRestoreError.InvalidBackup(BackupValidationError.UnsupportedSchemaVersion(99)),
                (result as DomainResult.Failure).error,
            )
            assertEquals(listOf("existing-1"), database.exerciseDao().findAll().map { it.id })
        }
}
