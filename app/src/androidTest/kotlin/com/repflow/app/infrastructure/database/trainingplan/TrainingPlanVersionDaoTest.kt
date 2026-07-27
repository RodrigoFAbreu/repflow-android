package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [TrainingPlanVersionDao], run
 * against an in-memory Room database on an actual device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPlanVersionDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var planDao: TrainingPlanDao
    private lateinit var dao: TrainingPlanVersionDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        planDao = database.trainingPlanDao()
        dao = database.trainingPlanVersionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun insertPlan(id: String = "plan-1") {
        planDao.insert(
            TrainingPlanEntity(id = id, name = "Push Day", nameKey = "push day", createdAt = 1_000L, updatedAt = 1_000L),
        )
    }

    private fun version(
        id: String,
        planId: String = "plan-1",
        versionNumber: Int,
    ) = TrainingPlanVersionEntity(
        id = id,
        planId = planId,
        versionNumber = versionNumber,
        note = null,
        createdAt = 1_000L,
    )

    @Test
    fun findLatestForPlan_returnsTheHighestVersionNumber() =
        runBlocking {
            insertPlan()
            dao.insert(version(id = "v1", versionNumber = 1))
            dao.insert(version(id = "v2", versionNumber = 2))

            val latest = dao.findLatestForPlan("plan-1")

            assertEquals("v2", latest?.id)
        }

    @Test
    fun findLatestForPlan_returnsNullWhenThePlanHasNoVersions() =
        runBlocking {
            insertPlan()

            assertEquals(null, dao.findLatestForPlan("plan-1"))
        }

    @Test
    fun findAllForPlan_returnsEveryVersionOrderedAscending() =
        runBlocking {
            insertPlan()
            dao.insert(version(id = "v2", versionNumber = 2))
            dao.insert(version(id = "v1", versionNumber = 1))

            val all = dao.findAllForPlan("plan-1")

            assertEquals(listOf("v1", "v2"), all.map { it.id })
        }

    /**
     * Deleting a plan cascades to its versions (see [TrainingPlanVersionEntity]'s
     * `ON DELETE CASCADE` foreign key) - there is no delete DAO method yet
     * (no plan-delete feature exists in M2), so the cascade is exercised
     * directly via a raw statement to confirm the schema-level constraint
     * itself, independent of any application-layer feature.
     */
    @Test
    fun deletingAPlan_cascadesToItsVersions() =
        runBlocking {
            insertPlan()
            dao.insert(version(id = "v1", versionNumber = 1))

            database.openHelper.writableDatabase.execSQL("DELETE FROM training_plans WHERE id = 'plan-1'")

            assertEquals(emptyList<TrainingPlanVersionEntity>(), dao.findAllForPlan("plan-1"))
        }

    @Test(expected = Exception::class)
    fun insert_rejectsADuplicateVersionNumberForTheSamePlan() =
        runBlocking {
            insertPlan()
            dao.insert(version(id = "v1", versionNumber = 1))
            dao.insert(version(id = "v1-dup", versionNumber = 1))
        }
}
