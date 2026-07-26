package com.repflow.app.infrastructure.database.progression

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [ProgressionRecommendationDao], run
 * against an in-memory Room database on an actual device/emulator,
 * mirroring `RecoveryDaoTest`.
 */
@RunWith(AndroidJUnit4::class)
class ProgressionRecommendationDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var dao: ProgressionRecommendationDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        dao = database.progressionRecommendationDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun findLatestForExerciseReturnsTheMostRecentlyComputedRow() =
        runBlocking {
            dao.insert(entity(id = "rec-1", exerciseId = "exercise-1", computedAt = 1000))
            dao.insert(entity(id = "rec-2", exerciseId = "exercise-1", computedAt = 2000))
            dao.insert(entity(id = "rec-3", exerciseId = "exercise-2", computedAt = 5000))

            val latest = dao.findLatestForExercise("exercise-1")
            assertEquals("rec-2", latest?.id)
        }

    @Test
    fun findLatestForExerciseReturnsNullWhenNoRowsExist() =
        runBlocking {
            assertNull(dao.findLatestForExercise("exercise-1"))
        }

    @Test
    fun updatePersistsAManualOverride() =
        runBlocking {
            dao.insert(entity(id = "rec-1", exerciseId = "exercise-1", computedAt = 1000))

            val stored = dao.findLatestForExercise("exercise-1")!!
            dao.update(stored.copy(overrideResult = "maintain_load", overrideAt = 1500))

            val updated = dao.findLatestForExercise("exercise-1")
            assertEquals("maintain_load", updated?.overrideResult)
            assertEquals(1500L, updated?.overrideAt)
        }

    private fun entity(
        id: String,
        exerciseId: String,
        computedAt: Long,
    ): ProgressionRecommendationEntity =
        ProgressionRecommendationEntity(
            id = id,
            exerciseId = exerciseId,
            result = "increase_load",
            reasons = "hit top of range",
            policyVersion = 1,
            computedAt = computedAt,
            overrideResult = null,
            overrideAt = null,
        )
}
