package com.repflow.app.data.workout

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.workout.WorkoutReadFailedException
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Real-SQLite coverage that a failed read leaves [LocalWorkoutRepository] as
 * the application-layer [WorkoutReadFailedException], which the workout screen
 * reports, and that cancellation still passes through its catch untouched.
 */
@RunWith(AndroidJUnit4::class)
class LocalWorkoutRepositoryReadFailureTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var repository: LocalWorkoutRepository

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        repository =
            LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    /**
     * Fails the read in SQLite itself ("no such table"). Closing the database
     * would not do: Room 2.8 then fails a suspend read with a
     * `CancellationException`, which never reaches the repository's catch.
     */
    private fun dropTable(name: String) {
        database.openHelper.writableDatabase.execSQL("DROP TABLE $name")
    }

    @Test
    fun findByIdThrowsWorkoutReadFailedWhenSqliteFails() {
        dropTable("workout_sessions")

        assertThrows(WorkoutReadFailedException::class.java) {
            runBlocking { repository.findById(WorkoutSessionId("session")) }
        }
    }

    @Test
    fun findActiveSessionThrowsWorkoutReadFailedWhenSqliteFails() {
        dropTable("workout_sessions")

        assertThrows(WorkoutReadFailedException::class.java) {
            runBlocking { repository.findActiveSession() }
        }
    }

    /** The session row reads fine and its exercises don't: the whole read is covered, not only the first query. */
    @Test
    fun findByIdThrowsWorkoutReadFailedWhenLoadingTheSessionsExercisesFails() {
        val session = WorkoutSession.start(WorkoutSessionId("session"), null, Instant.parse("2026-01-01T00:00:00Z"))
        runBlocking { repository.insert(session) }
        dropTable("workout_exercises")

        assertThrows(WorkoutReadFailedException::class.java) {
            runBlocking { repository.findById(session.id) }
        }
    }

    @Test
    fun findByIdLetsCancellationThrough() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                cancel()
                repository.findById(WorkoutSessionId("session"))
            }
        }
    }

    @Test
    fun findActiveSessionLetsCancellationThrough() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                cancel()
                repository.findActiveSession()
            }
        }
    }
}
