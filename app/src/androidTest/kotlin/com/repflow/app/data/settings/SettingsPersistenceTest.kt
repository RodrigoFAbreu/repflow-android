package com.repflow.app.data.settings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.backup.EraseAllData
import com.repflow.app.application.backup.RestoreBackup
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.data.backup.LocalBackupRepository
import com.repflow.app.data.backup.LocalTrainingDataRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.SETTINGS_SEED_CALLBACK
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite coverage for remediation-1 CP14's preferences row and the two
 * destructive paths that must leave it alone, over an in-memory database built
 * with the production seed callback:
 *
 * - a fresh install has the pinned row, at [AppSettings.DEFAULT];
 * - `Erase all data` empties every training-data table - an active session
 *   included - and leaves the settings row as it was;
 * - restoring a backup taken before this milestone (the unchanged schema-2
 *   transfer shape) restores all ten collections *and* leaves the settings
 *   row present and unchanged - the regression no test could see before this
 *   milestone, because until CP14 no table outside the snapshot existed.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var settings: LocalSettingsRepository
    private lateinit var trainingData: LocalTrainingDataRepository

    private val customised =
        AppSettings(
            restTimerAutoStart = false,
            restTimerVibrate = false,
            restTimerNotification = true,
            keepScreenAwake = true,
            confirmBeforeFinishing = false,
        )

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .addCallback(SETTINGS_SEED_CALLBACK)
                .build()
        settings = LocalSettingsRepository(database)
        trainingData = LocalTrainingDataRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aFreshDatabaseHasThePinnedRowAtTheDefaults() =
        runBlocking {
            assertNotNull("the seed callback must create the row", database.settingsDao().find())
            assertEquals(AppSettings.DEFAULT, settings.get())
        }

    @Test
    fun anUpdateIsStoredAndObserved() =
        runBlocking {
            assertEquals(DomainResult.Success(Unit), settings.update { customised })

            assertEquals(customised, settings.get())
            assertEquals(customised, settings.observe().first())
        }

    @Test
    fun eraseAllDataEmptiesEveryTrainingTableDiscardsTheActiveSessionAndKeepsTheSettings() =
        runBlocking {
            settings.update { customised }
            seedOneRowPerTrainingTable(activeSession = true)
            assertEquals(1, count("workout_sessions WHERE status = 'ACTIVE'"))

            val result = EraseAllData(trainingData)()

            assertEquals(DomainResult.Success(Unit), result)
            TRAINING_TABLES.forEach { table -> assertEquals("$table must be empty", 0, count(table)) }
            assertEquals(1, count("settings"))
            assertEquals(customised, settings.get())
        }

    @Test
    fun restoringABackupFromBeforeThisMilestoneRestoresTheTenCollectionsAndKeepsTheSettings() =
        runBlocking {
            settings.update { customised }
            seedOneRowPerTrainingTable(activeSession = false, idPrefix = "old-")
            val restore = RestoreBackup(LocalBackupRepository(database, trainingData))

            val result = restore(PRE_MILESTONE_BACKUP_JSON)

            assertTrue("restore failed: $result", result is DomainResult.Success)
            TRAINING_TABLES.forEach { table -> assertEquals("$table must hold the backup's one row", 1, count(table)) }
            assertEquals(0, count("exercises WHERE id LIKE 'old-%'"))
            assertEquals(1, count("progression_recommendations WHERE id = 'rec-1'"))
            assertEquals("the settings row must survive the restore", 1, count("settings"))
            assertEquals(customised, settings.get())
        }

    /**
     * Implementation-review revision 1's O3: [TRAINING_TABLES] - and so the
     * erase and restore tests above, and `TrainingDataDao`'s hand-listed
     * deletes they exercise - must cover every table the schema registers
     * except `settings` and SQLite's/Room's own bookkeeping. A training table
     * added later fails here until it joins the list, and then fails the erase
     * test until the scoped clear deletes it.
     */
    @Test
    fun theTrainingTablesAreEveryRegisteredTableButTheSettings() {
        val registered =
            database.openHelper.readableDatabase
                .query("SELECT name FROM sqlite_master WHERE type = 'table'")
                .use { cursor -> generateSequence { if (cursor.moveToNext()) cursor.getString(0) else null }.toSet() }
        val bookkeeping = setOf("settings", "room_master_table", "android_metadata")

        assertEquals(
            TRAINING_TABLES.toSet(),
            registered.filterNot { it in bookkeeping || it.startsWith("sqlite_") }.toSet(),
        )
    }

    private fun count(tableAndWhere: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tableAndWhere").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun seedOneRowPerTrainingTable(
        activeSession: Boolean,
        idPrefix: String = "",
    ) {
        val db = database.openHelper.writableDatabase
        val p = idPrefix
        db.execSQL(
            "INSERT INTO exercises (id, name, name_key, tracking_type, instructions, default_load_increment_grams, " +
                "default_rest_seconds, origin, archived_at, created_at, updated_at) VALUES ('${p}ex', 'Squat', '${p}squat', " +
                "'WEIGHT_AND_REPS', NULL, NULL, NULL, 'CUSTOM', NULL, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO training_plans (id, name, name_key, created_at, updated_at, archived_at) " +
                "VALUES ('${p}plan', 'Legs', '${p}legs', 1, 1, NULL)",
        )
        db.execSQL(
            "INSERT INTO training_plan_versions (id, plan_id, version_number, note, created_at) VALUES ('${p}v', '${p}plan', 1, NULL, 1)",
        )
        db.execSQL(
            "INSERT INTO planned_exercises (id, version_id, exercise_id, sort_order, target_sets, target_kind, rep_min, rep_max, " +
                "duration_min_seconds, duration_max_seconds, rest_seconds, is_optional, target_warmup_sets) " +
                "VALUES ('${p}pe', '${p}v', '${p}ex', 0, 3, 'REPS', 5, 8, NULL, NULL, 120, 0, NULL)",
        )
        val status = if (activeSession) "'ACTIVE', 10, NULL" else "'COMPLETED', 10, 20"
        db.execSQL(
            "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at, " +
                "rest_timer_end_at_epoch_ms, rest_timer_total_duration_seconds, invalidated_at) " +
                "VALUES ('${p}s', '${p}v', $status, NULL, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO workout_exercises (id, session_id, exercise_id, sort_order, exercise_name_snapshot, tracking_type, " +
                "planned_exercise_id) VALUES ('${p}we', '${p}s', '${p}ex', 0, 'Squat', 'WEIGHT_AND_REPS', '${p}pe')",
        )
        db.execSQL(
            "INSERT INTO workout_sets (id, workout_exercise_id, sort_order, load, reps, duration_seconds, rpe, is_warmup, " +
                "created_at, updated_at, pain, technique_quality) VALUES ('${p}set', '${p}we', 0, 100.0, 5, NULL, NULL, 0, 11, 11, NULL, NULL)",
        )
        db.execSQL(
            "INSERT INTO recovery_entries (id, entry_date, sleep_quality, energy, leg_doms, heel_stiffness, pain_while_walking, " +
                "heavy_legs, futsal_in_previous_24h, futsal_expected_next_24h, notes, created_at, updated_at) " +
                "VALUES ('${p}r', '2025-12-01', 3, 3, 1, 0, 0, 1, 0, 0, NULL, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO futsal_sessions (id, entry_date, duration_minutes, session_rpe, created_at, updated_at) " +
                "VALUES ('${p}f', '2025-12-01', 60, 7.0, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO progression_recommendations (id, exercise_id, result, reasons, policy_version, computed_at, " +
                "override_result, override_at) VALUES ('${p}rec', '${p}ex', 'maintain_load', 'held', 1, 30, NULL, NULL)",
        )
    }

    private companion object {
        val TRAINING_TABLES =
            listOf(
                "exercises",
                "training_plans",
                "training_plan_versions",
                "planned_exercises",
                "workout_sessions",
                "workout_exercises",
                "workout_sets",
                "recovery_entries",
                "futsal_sessions",
                "progression_recommendations",
            )

        /**
         * A backup in the transfer shape every build since Milestone 8 writes -
         * schema version 2, which this milestone does not change - with one row
         * in each of the ten collections and, of course, no settings.
         */
        val PRE_MILESTONE_BACKUP_JSON =
            """
            {
              "schemaVersion": 2,
              "exercises": [
                {
                  "id": "ex-1", "name": "Bench Press", "nameKey": "bench press",
                  "trackingType": "WEIGHT_AND_REPS", "instructions": null,
                  "defaultLoadIncrementGrams": 2500, "defaultRestSeconds": 120,
                  "origin": "CUSTOM", "archivedAt": null, "createdAt": 1, "updatedAt": 1
                }
              ],
              "trainingPlans": [
                {"id": "plan-1", "name": "Push Day", "nameKey": "push day", "createdAt": 1, "updatedAt": 1, "archivedAt": null}
              ],
              "trainingPlanVersions": [
                {"id": "v-1", "planId": "plan-1", "versionNumber": 1, "note": null, "createdAt": 1}
              ],
              "plannedExercises": [
                {
                  "id": "pe-1", "versionId": "v-1", "exerciseId": "ex-1", "sortOrder": 0,
                  "targetSets": 3, "targetKind": "REPS", "repMin": 8, "repMax": 12,
                  "durationMinSeconds": null, "durationMaxSeconds": null, "restSeconds": 90,
                  "isOptional": false, "targetWarmupSets": 1
                }
              ],
              "workoutSessions": [
                {
                  "id": "session-1", "trainingPlanVersionId": "v-1", "status": "COMPLETED",
                  "startedAt": 10, "endedAt": 20, "restTimerEndAtEpochMs": null,
                  "restTimerTotalDurationSeconds": null, "invalidatedAt": null
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
                  "createdAt": 11, "updatedAt": 12, "pain": 1, "techniqueQuality": 4
                }
              ],
              "recoveryEntries": [
                {
                  "id": "recovery-1", "entryDate": "2026-01-01", "sleepQuality": 4, "energy": 3,
                  "legDoms": 1, "heelStiffness": 0, "painWhileWalking": 0, "heavyLegs": 1,
                  "futsalInPrevious24h": false, "futsalExpectedNext24h": false, "notes": null,
                  "createdAt": 5, "updatedAt": 5
                }
              ],
              "futsalSessions": [
                {"id": "futsal-1", "entryDate": "2026-01-02", "durationMinutes": 50, "sessionRpe": 8.0, "createdAt": 6, "updatedAt": 6}
              ],
              "progressionRecommendations": [
                {
                  "id": "rec-1", "exerciseId": "ex-1", "result": "increase_load", "reasons": "hit the top of the range",
                  "policyVersion": 1, "computedAt": 21, "overrideResult": null, "overrideAt": null
                }
              ]
            }
            """.trimIndent()
    }
}
