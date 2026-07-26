package com.repflow.app.infrastructure.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Validates [MIGRATION_1_2] against a real v1-schema database, using the
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

    private fun insertV1Exercise(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO exercises (id, name, name_key, tracking_type, instructions, " +
                "default_load_increment_grams, default_rest_seconds, origin, archived_at, created_at, updated_at) " +
                "VALUES ('exercise-1', 'Bench Press', 'bench press', 'WEIGHT_AND_REPS', NULL, " +
                "NULL, NULL, 'CUSTOM', NULL, 1000, 1000)",
        )
    }
}
