package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [TrainingPlanDao], run against an
 * in-memory Room database on an actual device/emulator, mirroring
 * `ExerciseDaoTest`.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPlanDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var dao: TrainingPlanDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        dao = database.trainingPlanDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entity(
        id: String,
        name: String,
        nameKey: String,
    ) = TrainingPlanEntity(
        id = id,
        name = name,
        nameKey = nameKey,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun observeAll_returnsRowsOrderedByNameKeyThenId() =
        runBlocking {
            dao.insert(entity(id = "b", name = "Push Day", nameKey = "push day"))
            dao.insert(entity(id = "a", name = "Leg Day", nameKey = "leg day"))

            val plans = dao.observeAll().first()

            assertEquals(listOf("leg day", "push day"), plans.map { it.nameKey })
        }

    @Test(expected = Exception::class)
    fun insert_rejectsADuplicateNameKey() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Push Day", nameKey = "push day"))
            dao.insert(entity(id = "b", name = "PUSH DAY", nameKey = "push day"))
        }

    @Test
    fun update_returnsZeroRowsChangedWhenTheIdDoesNotExist() =
        runBlocking {
            val rowsUpdated = dao.update(entity(id = "missing", name = "Ghost", nameKey = "ghost"))

            assertEquals(0, rowsUpdated)
        }

    @Test
    fun update_returnsOneRowChangedForAnExistingId() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Push Day", nameKey = "push day"))

            val rowsUpdated = dao.update(entity(id = "a", name = "Push Day A", nameKey = "push day a"))

            assertEquals(1, rowsUpdated)
        }

    @Test
    fun findById_returnsTheMatchingRow() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Push Day", nameKey = "push day"))

            assertEquals("push day", dao.findById("a")?.nameKey)
        }

    @Test
    fun findById_returnsNullWhenNoRowMatches() =
        runBlocking {
            assertEquals(null, dao.findById("missing"))
        }

    @Test
    fun findIdByNameKey_returnsTheMatchingId() =
        runBlocking {
            dao.insert(entity(id = "a", name = "Push Day", nameKey = "push day"))

            assertEquals("a", dao.findIdByNameKey("push day"))
        }

    @Test
    fun findIdByNameKey_returnsNullWhenNoRowMatches() =
        runBlocking {
            assertEquals(null, dao.findIdByNameKey("push day"))
        }
}
