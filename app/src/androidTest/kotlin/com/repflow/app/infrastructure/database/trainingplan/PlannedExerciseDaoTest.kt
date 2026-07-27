package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [PlannedExerciseDao], run against
 * an in-memory Room database on an actual device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class PlannedExerciseDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var exerciseDao: ExerciseDao
    private lateinit var planDao: TrainingPlanDao
    private lateinit var versionDao: TrainingPlanVersionDao
    private lateinit var dao: PlannedExerciseDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        exerciseDao = database.exerciseDao()
        planDao = database.trainingPlanDao()
        versionDao = database.trainingPlanVersionDao()
        dao = database.plannedExerciseDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private suspend fun seedPlanAndVersion() {
        exerciseDao.insert(
            ExerciseEntity(
                id = "exercise-1",
                name = "Bench Press",
                nameKey = "bench press",
                trackingType = "WEIGHT_AND_REPS",
                instructions = null,
                defaultLoadIncrementGrams = null,
                defaultRestSeconds = null,
                origin = "CUSTOM",
                archivedAt = null,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )
        planDao.insert(
            TrainingPlanEntity(id = "plan-1", name = "Push Day", nameKey = "push day", createdAt = 1_000L, updatedAt = 1_000L),
        )
        versionDao.insert(
            TrainingPlanVersionEntity(id = "version-1", planId = "plan-1", versionNumber = 1, note = null, createdAt = 1_000L),
        )
    }

    private fun row(
        id: String,
        sortOrder: Int,
    ) = PlannedExerciseEntity(
        id = id,
        versionId = "version-1",
        exerciseId = "exercise-1",
        sortOrder = sortOrder,
        targetSets = 3,
        targetKind = "REPS",
        repMin = 8,
        repMax = 12,
        durationMinSeconds = null,
        durationMaxSeconds = null,
        restSeconds = 90,
        isOptional = false,
    )

    @Test
    fun insertAll_thenFindAllForVersion_returnsRowsOrderedBySortOrder() =
        runBlocking {
            seedPlanAndVersion()

            dao.insertAll(listOf(row(id = "b", sortOrder = 1), row(id = "a", sortOrder = 0)))

            val rows = dao.findAllForVersion("version-1")
            assertEquals(listOf("a", "b"), rows.map { it.id })
        }

    @Test
    fun findAllForVersion_returnsEmptyForAVersionWithNoRows() =
        runBlocking {
            seedPlanAndVersion()

            assertEquals(emptyList<PlannedExerciseEntity>(), dao.findAllForVersion("version-1"))
        }

    /**
     * Deleting a version cascades to its planned exercises (see
     * [PlannedExerciseEntity]'s `ON DELETE CASCADE` foreign key to
     * `training_plan_versions`).
     */
    @Test
    fun deletingAVersion_cascadesToItsPlannedExercises() =
        runBlocking {
            seedPlanAndVersion()
            dao.insertAll(listOf(row(id = "a", sortOrder = 0)))

            database.openHelper.writableDatabase.execSQL("DELETE FROM training_plan_versions WHERE id = 'version-1'")

            assertEquals(emptyList<PlannedExerciseEntity>(), dao.findAllForVersion("version-1"))
        }

    @Test(expected = Exception::class)
    fun insertAll_rejectsADuplicateSortOrderWithinTheSameVersion() =
        runBlocking {
            seedPlanAndVersion()

            dao.insertAll(listOf(row(id = "a", sortOrder = 0), row(id = "b", sortOrder = 0)))
        }
}
