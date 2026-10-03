package com.repflow.app.infrastructure.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.infrastructure.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 * Validates every migration ([MIGRATION_1_2] through [MIGRATION_8_9]) against a real older-schema database, using the
 * exported schema JSON files under `app/schemas` (see the `androidTest`
 * `assets.directories` entry in `app/build.gradle.kts`). This is the first
 * real migration test in the project - version 1 never had one, and there
 * is no `fallbackToDestructiveMigration` anywhere (see [RepFlowDatabase]'s
 * KDoc).
 */
class RepFlowDatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            RepFlowDatabase::class.java,
        )

    private companion object {
        const val TEST_DB = "migration-test"
        const val REGISTRATION_DB = "migration-8-9-registration-test"

        /** The pinned v8 row with `keep_screen_awake = 1` (its default is 0), so a bypassed migration is visible. */
        const val SEED_V8_ROW =
            "INSERT INTO settings (id, rest_timer_auto_start, rest_timer_vibrate, rest_timer_notification, " +
                "keep_screen_awake, confirm_before_finishing) VALUES (1, 1, 1, 1, 1, 1)"
    }

    @Test
    fun migrate1To2_preservesExistingExerciseRowsAndAddsTheNewTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        val cursor = migratedDb.query("SELECT id, name FROM exercises")
        assertEquals(1, cursor.count)
        cursor.moveToFirst()
        assertEquals("exercise-1", cursor.getString(0))
        assertEquals("Bench Press", cursor.getString(1))
        cursor.close()

        // The three new tables exist and are empty and writable post-migration.
        val plansCursor = migratedDb.query("SELECT COUNT(*) FROM training_plans")
        plansCursor.moveToFirst()
        assertEquals(0, plansCursor.getInt(0))
        plansCursor.close()
    }

    @Test
    fun migrate1To2_allowsInsertingATrainingPlanAndItsVersionAfterMigration() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        migratedDb.execSQL(
            "INSERT INTO training_plans (id, name, name_key, created_at, updated_at) " +
                "VALUES ('plan-1', 'Push Day', 'push day', 1000, 1000)",
        )
        migratedDb.execSQL(
            "INSERT INTO training_plan_versions (id, plan_id, version_number, note, created_at) " +
                "VALUES ('version-1', 'plan-1', 1, NULL, 1000)",
        )
        migratedDb.execSQL(
            "INSERT INTO planned_exercises (id, version_id, exercise_id, sort_order, target_sets, target_kind, " +
                "rep_min, rep_max, duration_min_seconds, duration_max_seconds, rest_seconds, is_optional) " +
                "VALUES ('planned-1', 'version-1', 'exercise-1', 0, 3, 'REPS', 8, 12, NULL, NULL, 90, 0)",
        )

        val cursor = migratedDb.query("SELECT COUNT(*) FROM planned_exercises WHERE version_id = 'version-1'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun migrate2To3_preservesExistingTablesAndAddsTheWorkoutTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        val exercisesCursor = migratedDb.query("SELECT COUNT(*) FROM exercises")
        exercisesCursor.moveToFirst()
        assertEquals(1, exercisesCursor.getInt(0))
        exercisesCursor.close()

        val sessionsCursor = migratedDb.query("SELECT COUNT(*) FROM workout_sessions")
        sessionsCursor.moveToFirst()
        assertEquals(0, sessionsCursor.getInt(0))
        sessionsCursor.close()
    }

    @Test
    fun migrate2To3_allowsInsertingAnAdHocWorkoutSessionWithAnExerciseAndASet() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        migratedDb.execSQL(
            "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at) " +
                "VALUES ('session-1', NULL, 'ACTIVE', 1000, NULL)",
        )
        migratedDb.execSQL(
            "INSERT INTO workout_exercises (id, session_id, exercise_id, sort_order, exercise_name_snapshot, " +
                "tracking_type, planned_exercise_id) " +
                "VALUES ('we-1', 'session-1', 'exercise-1', 0, 'Bench Press', 'WEIGHT_AND_REPS', NULL)",
        )
        migratedDb.execSQL(
            "INSERT INTO workout_sets (id, workout_exercise_id, sort_order, load, reps, duration_seconds, rpe, " +
                "is_warmup, created_at, updated_at) " +
                "VALUES ('set-1', 'we-1', 0, 60.0, 8, NULL, NULL, 0, 1000, 1000)",
        )

        val cursor = migratedDb.query("SELECT COUNT(*) FROM workout_sets WHERE workout_exercise_id = 'we-1'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()
    }

    @Test
    fun migrate3To4_preservesExistingWorkoutRowsAndAddsNullableRestTimerColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            execSQL(
                "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at) " +
                    "VALUES ('session-1', NULL, 'ACTIVE', 1000, NULL)",
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)

        val cursor =
            migratedDb.query(
                "SELECT rest_timer_end_at_epoch_ms, rest_timer_total_duration_seconds " +
                    "FROM workout_sessions WHERE id = 'session-1'",
            )
        cursor.moveToFirst()
        assertEquals(true, cursor.isNull(0))
        assertEquals(true, cursor.isNull(1))
        cursor.close()
    }

    @Test
    fun migrate3To4_allowsUpdatingTheNewRestTimerColumns() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            execSQL(
                "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at) " +
                    "VALUES ('session-1', NULL, 'ACTIVE', 1000, NULL)",
            )
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        migratedDb.execSQL(
            "UPDATE workout_sessions SET rest_timer_end_at_epoch_ms = 91000, " +
                "rest_timer_total_duration_seconds = 90 WHERE id = 'session-1'",
        )

        val cursor =
            migratedDb.query(
                "SELECT rest_timer_end_at_epoch_ms, rest_timer_total_duration_seconds " +
                    "FROM workout_sessions WHERE id = 'session-1'",
            )
        cursor.moveToFirst()
        assertEquals(91000L, cursor.getLong(0))
        assertEquals(90, cursor.getInt(1))
        cursor.close()
    }

    @Test
    fun migrate4To5_addsRecoveryAndFutsalTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        migratedDb.execSQL(
            "INSERT INTO recovery_entries (id, entry_date, sleep_quality, energy, leg_doms, heel_stiffness, " +
                "pain_while_walking, heavy_legs, futsal_in_previous_24h, futsal_expected_next_24h, notes, " +
                "created_at, updated_at) VALUES ('recovery-1', '2026-01-01', 3, 3, 1, 0, 0, 1, 0, 0, NULL, 1000, 1000)",
        )
        migratedDb.execSQL(
            "INSERT INTO futsal_sessions (id, entry_date, duration_minutes, session_rpe, created_at, updated_at) " +
                "VALUES ('futsal-1', '2026-01-01', 60, 6.0, 1000, 1000)",
        )

        val recoveryCursor = migratedDb.query("SELECT sleep_quality FROM recovery_entries WHERE id = 'recovery-1'")
        recoveryCursor.moveToFirst()
        assertEquals(3, recoveryCursor.getInt(0))
        recoveryCursor.close()

        val futsalCursor = migratedDb.query("SELECT duration_minutes FROM futsal_sessions WHERE id = 'futsal-1'")
        futsalCursor.moveToFirst()
        assertEquals(60, futsalCursor.getInt(0))
        futsalCursor.close()
    }

    @Test
    fun migrate5To6_addsProgressionRecommendationsTable() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
        migratedDb.execSQL(
            "INSERT INTO progression_recommendations (id, exercise_id, result, reasons, policy_version, " +
                "computed_at, override_result, override_at) VALUES ('rec-1', 'exercise-1', 'increase_load', " +
                "'hit top of range', 1, 1000, NULL, NULL)",
        )

        val cursor =
            migratedDb.query("SELECT result FROM progression_recommendations WHERE id = 'rec-1'")
        cursor.moveToFirst()
        assertEquals("increase_load", cursor.getString(0))
        cursor.close()
    }

    @Test
    fun migrate6To7_preservesExistingRowsAndAddsNullableColumns() {
        seedDatabaseThroughVersion6()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)

        val planCursor = migratedDb.query("SELECT archived_at FROM training_plans WHERE id = 'plan-1'")
        planCursor.moveToFirst()
        assertEquals(true, planCursor.isNull(0))
        planCursor.close()

        val plannedExerciseCursor =
            migratedDb.query("SELECT target_warmup_sets FROM planned_exercises WHERE id = 'planned-1'")
        plannedExerciseCursor.moveToFirst()
        assertEquals(true, plannedExerciseCursor.isNull(0))
        plannedExerciseCursor.close()

        val sessionCursor = migratedDb.query("SELECT invalidated_at FROM workout_sessions WHERE id = 'session-1'")
        sessionCursor.moveToFirst()
        assertEquals(true, sessionCursor.isNull(0))
        sessionCursor.close()

        val setCursor = migratedDb.query("SELECT pain, technique_quality FROM workout_sets WHERE id = 'set-1'")
        setCursor.moveToFirst()
        assertEquals(true, setCursor.isNull(0))
        assertEquals(true, setCursor.isNull(1))
        setCursor.close()
    }

    @Test
    fun migrate6To7_allowsUpdatingTheNewColumns() {
        seedDatabaseThroughVersion6()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
        migratedDb.execSQL("UPDATE training_plans SET archived_at = 5000 WHERE id = 'plan-1'")
        migratedDb.execSQL("UPDATE planned_exercises SET target_warmup_sets = 2 WHERE id = 'planned-1'")
        migratedDb.execSQL("UPDATE workout_sessions SET invalidated_at = 6000 WHERE id = 'session-1'")
        migratedDb.execSQL("UPDATE workout_sets SET pain = 3, technique_quality = 4 WHERE id = 'set-1'")

        val planCursor = migratedDb.query("SELECT archived_at FROM training_plans WHERE id = 'plan-1'")
        planCursor.moveToFirst()
        assertEquals(5000L, planCursor.getLong(0))
        planCursor.close()

        val plannedExerciseCursor =
            migratedDb.query("SELECT target_warmup_sets FROM planned_exercises WHERE id = 'planned-1'")
        plannedExerciseCursor.moveToFirst()
        assertEquals(2, plannedExerciseCursor.getInt(0))
        plannedExerciseCursor.close()

        val sessionCursor = migratedDb.query("SELECT invalidated_at FROM workout_sessions WHERE id = 'session-1'")
        sessionCursor.moveToFirst()
        assertEquals(6000L, sessionCursor.getLong(0))
        sessionCursor.close()

        val setCursor = migratedDb.query("SELECT pain, technique_quality FROM workout_sets WHERE id = 'set-1'")
        setCursor.moveToFirst()
        assertEquals(3, setCursor.getInt(0))
        assertEquals(4, setCursor.getInt(1))
        setCursor.close()
    }

    /**
     * Remediation-1 CP14: the single-row `settings` table arrives with today's
     * defaults - auto-start, vibrate and notification on, keep screen awake off,
     * confirm before finishing on - and every existing row survives.
     */
    @Test
    fun migrate7To8_addsTheSettingsTableWithTheDefaultRowAndKeepsExistingRows() {
        seedDatabaseThroughVersion6()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)

        val settingsCursor =
            migratedDb.query(
                "SELECT id, rest_timer_auto_start, rest_timer_vibrate, rest_timer_notification, " +
                    "keep_screen_awake, confirm_before_finishing FROM settings",
            )
        assertEquals(1, settingsCursor.count)
        settingsCursor.moveToFirst()
        assertEquals(1, settingsCursor.getInt(0))
        assertEquals(listOf(1, 1, 1, 0, 1), (1..5).map { settingsCursor.getInt(it) })
        settingsCursor.close()

        val exerciseCursor = migratedDb.query("SELECT name FROM exercises WHERE id = 'exercise-1'")
        exerciseCursor.moveToFirst()
        assertEquals("Bench Press", exerciseCursor.getString(0))
        exerciseCursor.close()

        val setCursor = migratedDb.query("SELECT load, reps FROM workout_sets WHERE id = 'set-1'")
        setCursor.moveToFirst()
        assertEquals(60.0, setCursor.getDouble(0), 0.0)
        assertEquals(8, setCursor.getInt(1))
        setCursor.close()
    }

    @Test
    fun migrate7To8_allowsUpdatingTheSettingsRow() {
        seedDatabaseThroughVersion6()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8)
        migratedDb.execSQL("UPDATE settings SET keep_screen_awake = 1, rest_timer_vibrate = 0 WHERE id = 1")

        val cursor = migratedDb.query("SELECT keep_screen_awake, rest_timer_vibrate FROM settings WHERE id = 1")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        assertEquals(0, cursor.getInt(1))
        cursor.close()
    }

    @Test
    fun migrate8To9_keepsTheFiveSwitchesAndGivesTheNewColumnsTheirDefaults() {
        helper.createDatabase(TEST_DB, 8).apply {
            execSQL(SEED_V8_ROW)
            close()
        }

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        migratedDb
            .query(
                "SELECT rest_timer_auto_start, rest_timer_vibrate, rest_timer_notification, keep_screen_awake, " +
                    "confirm_before_finishing, theme, default_rest_seconds, extra_set_fields, last_backup_at FROM settings",
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(listOf(1, 1, 1, 1, 1), (0..4).map { cursor.getInt(it) })
                assertEquals("SYSTEM", cursor.getString(5))
                assertEquals(90, cursor.getInt(6))
                assertEquals("COLLAPSED", cursor.getString(7))
                assertEquals(true, cursor.isNull(8))
            }
    }

    @Test
    fun migrate7To9_givesTheSeededRowTheNewDefaults() {
        seedDatabaseThroughVersion6()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).close()
        helper.runMigrationsAndValidate(TEST_DB, 8, true, MIGRATION_7_8).close()

        val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 9, true, MIGRATION_8_9)

        migratedDb.query("SELECT theme, default_rest_seconds, extra_set_fields, last_backup_at FROM settings").use { cursor ->
            cursor.moveToFirst()
            assertEquals("SYSTEM", cursor.getString(0))
            assertEquals(90, cursor.getInt(1))
            assertEquals("COLLAPSED", cursor.getString(2))
            assertEquals(true, cursor.isNull(3))
        }
    }

    @Test
    fun fullChainFrom1To9OpensThroughAllMigrations() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, RepFlowDatabase.VERSION, true, *ALL_MIGRATIONS).use { db ->
            db.query("SELECT COUNT(*) FROM settings").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    @Test
    fun allMigrationsContainsEveryStepUpToTheDatabaseVersion() {
        val steps = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }

        assertEquals((1 until RepFlowDatabase.VERSION).map { it to it + 1 }, steps)
        assertEquals(ALL_MIGRATIONS.size, ALL_MIGRATIONS.toSet().size)
    }

    /**
     * The production-registration test: a real version-8 file (with one
     * non-default value) opened through [DatabaseModule.buildRepFlowDatabase]
     * only opens, and reads the new defaults, if the production builder
     * registers MIGRATION_8_9.
     */
    @Test
    fun theProductionBuilderUpgradesARealVersion8DatabaseToTheNewDefaults() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        helper.createDatabase(REGISTRATION_DB, 8).apply {
            execSQL(SEED_V8_ROW)
            close()
        }

        val database = DatabaseModule.buildRepFlowDatabase(targetContext, REGISTRATION_DB)
        try {
            val settings = checkNotNull(runBlocking { database.settingsDao().find() })

            assertEquals(ThemeMode.SYSTEM.name, settings.theme)
            assertEquals(90, settings.defaultRestSeconds)
            assertEquals(ExtraSetFields.COLLAPSED.name, settings.extraSetFields)
            assertNull(settings.lastBackupAt)
            assertEquals(true, settings.keepScreenAwake)
            assertEquals(true, settings.restTimerAutoStart)
            assertEquals(true, settings.restTimerVibrate)
            assertEquals(true, settings.restTimerNotification)
            assertEquals(true, settings.confirmBeforeFinishing)
        } finally {
            database.close()
            targetContext.deleteDatabase(REGISTRATION_DB)
        }
    }

    private fun seedDatabaseThroughVersion6() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Exercise(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).apply {
            execSQL(
                "INSERT INTO training_plans (id, name, name_key, created_at, updated_at) " +
                    "VALUES ('plan-1', 'Push Day', 'push day', 1000, 1000)",
            )
            execSQL(
                "INSERT INTO training_plan_versions (id, plan_id, version_number, note, created_at) " +
                    "VALUES ('version-1', 'plan-1', 1, NULL, 1000)",
            )
            execSQL(
                "INSERT INTO planned_exercises (id, version_id, exercise_id, sort_order, target_sets, " +
                    "target_kind, rep_min, rep_max, duration_min_seconds, duration_max_seconds, rest_seconds, " +
                    "is_optional) VALUES ('planned-1', 'version-1', 'exercise-1', 0, 3, 'REPS', 8, 12, NULL, " +
                    "NULL, 90, 0)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3).apply {
            execSQL(
                "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at) " +
                    "VALUES ('session-1', NULL, 'ACTIVE', 1000, NULL)",
            )
            execSQL(
                "INSERT INTO workout_exercises (id, session_id, exercise_id, sort_order, " +
                    "exercise_name_snapshot, tracking_type, planned_exercise_id) " +
                    "VALUES ('we-1', 'session-1', 'exercise-1', 0, 'Bench Press', 'WEIGHT_AND_REPS', NULL)",
            )
            execSQL(
                "INSERT INTO workout_sets (id, workout_exercise_id, sort_order, load, reps, duration_seconds, " +
                    "rpe, is_warmup, created_at, updated_at) " +
                    "VALUES ('set-1', 'we-1', 0, 60.0, 8, NULL, NULL, 0, 1000, 1000)",
            )
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4).close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).close()
        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).close()
    }

    private fun insertV1Exercise(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO exercises (id, name, name_key, tracking_type, instructions, " +
                "default_load_increment_grams, default_rest_seconds, origin, archived_at, created_at, updated_at) " +
                "VALUES ('exercise-1', 'Bench Press', 'bench press', 'WEIGHT_AND_REPS', NULL, " +
                "NULL, NULL, 'CUSTOM', NULL, 1000, 1000)",
        )
    }
}
