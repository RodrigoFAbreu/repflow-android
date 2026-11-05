package com.repflow.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.SETTINGS_SEED_CALLBACK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `hasTrainingData` is what decides whether a restore makes a safety copy and whether the dialog
 * promises one, so "no data" has to mean exactly that: the seeded `settings` row doesn't count, and a
 * single row in any parent table does. The child tables can't hold a row without their parent.
 */
@RunWith(AndroidJUnit4::class)
class LocalTrainingDataRepositoryTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalTrainingDataRepository

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .addCallback(SETTINGS_SEED_CALLBACK)
                .build()
        repository = LocalTrainingDataRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun anEmptyDatabaseWithItsSettingsRowHasNoTrainingData() =
        runBlocking<Unit> {
            val settingsRows =
                database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM settings").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
            assertEquals("The settings row must exist, or this test proves nothing", 1, settingsRows)

            assertFalse(repository.hasTrainingData())
        }

    @Test
    fun oneRowInEachParentTableInTurnCountsAsTrainingData() =
        runBlocking<Unit> {
            ONE_ROW_PER_PARENT_TABLE.forEach { (table, insert) ->
                assertFalse("Nothing should be left before the $table row", repository.hasTrainingData())

                database.openHelper.writableDatabase.execSQL(insert)
                assertTrue("One row in $table must count", repository.hasTrainingData())

                repository.clearTrainingData()
            }
            assertFalse(repository.hasTrainingData())
        }

    private companion object {
        val ONE_ROW_PER_PARENT_TABLE =
            mapOf(
                "exercises" to
                    "INSERT INTO exercises (id, name, name_key, tracking_type, instructions, default_load_increment_grams, " +
                    "default_rest_seconds, origin, archived_at, created_at, updated_at) VALUES ('ex', 'Squat', 'squat', " +
                    "'WEIGHT_AND_REPS', NULL, NULL, NULL, 'CUSTOM', NULL, 1, 1)",
                "training_plans" to
                    "INSERT INTO training_plans (id, name, name_key, created_at, updated_at, archived_at) " +
                    "VALUES ('plan', 'Legs', 'legs', 1, 1, NULL)",
                "workout_sessions" to
                    "INSERT INTO workout_sessions (id, training_plan_version_id, status, started_at, ended_at, " +
                    "rest_timer_end_at_epoch_ms, rest_timer_total_duration_seconds, invalidated_at) " +
                    "VALUES ('s', NULL, 'COMPLETED', 10, 20, NULL, NULL, NULL)",
                "recovery_entries" to
                    "INSERT INTO recovery_entries (id, entry_date, sleep_quality, energy, leg_doms, heel_stiffness, pain_while_walking, " +
                    "heavy_legs, futsal_in_previous_24h, futsal_expected_next_24h, notes, created_at, updated_at) " +
                    "VALUES ('r', '2025-12-01', 3, 3, 1, 0, 0, 1, 0, 0, NULL, 1, 1)",
                "futsal_sessions" to
                    "INSERT INTO futsal_sessions (id, entry_date, duration_minutes, session_rpe, created_at, updated_at) " +
                    "VALUES ('f', '2025-12-01', 60, 7.0, 1, 1)",
                "progression_recommendations" to
                    "INSERT INTO progression_recommendations (id, exercise_id, result, reasons, policy_version, computed_at, " +
                    "override_result, override_at) VALUES ('rec', 'ex', 'maintain_load', 'held', 1, 30, NULL, NULL)",
            )
    }
}
