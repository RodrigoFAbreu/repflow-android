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

    private fun insertV1Exercise(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO exercises (id, name, name_key, tracking_type, instructions, " +
                "default_load_increment_grams, default_rest_seconds, origin, archived_at, created_at, updated_at) " +
                "VALUES ('exercise-1', 'Bench Press', 'bench press', 'WEIGHT_AND_REPS', NULL, " +
                "NULL, NULL, 'CUSTOM', NULL, 1000, 1000)",
        )
    }
}
