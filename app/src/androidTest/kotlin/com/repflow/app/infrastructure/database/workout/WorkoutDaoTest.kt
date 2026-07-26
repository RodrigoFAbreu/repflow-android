package com.repflow.app.infrastructure.database.workout

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for the workout DAOs, run against an
 * in-memory Room database on an actual device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var exerciseDao: WorkoutExerciseDao
    private lateinit var setDao: WorkoutSetDao

    @Before
    fun createDatabase() =
        runBlocking {
            database =
                Room
                    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                    .build()
            sessionDao = database.workoutSessionDao()
            exerciseDao = database.workoutExerciseDao()
            setDao = database.workoutSetDao()

            database.exerciseDao().insert(
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
        }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun session(
        id: String = "session-1",
        status: String = "ACTIVE",
    ) = WorkoutSessionEntity(
        id = id,
        trainingPlanVersionId = null,
        status = status,
        startedAt = 1_000L,
        endedAt = null,
    )

    private fun exercise(
        id: String,
        sessionId: String,
        order: Int,
    ) = WorkoutExerciseEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = "exercise-1",
        sortOrder = order,
        exerciseNameSnapshot = "Bench Press",
        trackingType = "WEIGHT_AND_REPS",
        plannedExerciseId = null,
    )

    private fun set(
        id: String,
        workoutExerciseId: String,
        order: Int,
    ) = WorkoutSetEntity(
        id = id,
        workoutExerciseId = workoutExerciseId,
        sortOrder = order,
        load = 60.0,
        reps = 8,
        durationSeconds = null,
        rpe = null,
        isWarmup = false,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    @Test
    fun findActive_returnsTheSingleActiveSession() =
        runBlocking {
            sessionDao.insert(session())

            assertEquals("session-1", sessionDao.findActive()?.id)
        }

    @Test
    fun observeActive_emitsNullWhenTheSessionIsCompleted() =
        runBlocking {
            sessionDao.insert(session())
            sessionDao.update(session(status = "COMPLETED"))

            assertNull(sessionDao.observeActive().first())
        }

    @Test
    fun insertingExercisesAndSets_areRetrievableOrderedBySortOrder() =
        runBlocking {
            sessionDao.insert(session())
            exerciseDao.insertAll(listOf(exercise("we-2", "session-1", 1), exercise("we-1", "session-1", 0)))
            setDao.insertAll(listOf(set("set-2", "we-1", 1), set("set-1", "we-1", 0)))

            val exercises = exerciseDao.findAllForSession("session-1")
            val sets = setDao.findAllForExercise("we-1")

            assertEquals(listOf("we-1", "we-2"), exercises.map { it.id })
            assertEquals(listOf("set-1", "set-2"), sets.map { it.id })
        }

    @Test
    fun deleteForSession_cascadesToSets() =
        runBlocking {
            sessionDao.insert(session())
            exerciseDao.insertAll(listOf(exercise("we-1", "session-1", 0)))
            setDao.insertAll(listOf(set("set-1", "we-1", 0)))

            exerciseDao.deleteForSession("session-1")

            assertEquals(emptyList<WorkoutSetEntity>(), setDao.findAllForExercise("we-1"))
        }
}
