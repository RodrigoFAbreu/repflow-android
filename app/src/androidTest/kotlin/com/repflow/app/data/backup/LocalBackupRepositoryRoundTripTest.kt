package com.repflow.app.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Real-SQLite proof that a backup holds every workout session and restores it
 * unchanged: seeded through [LocalWorkoutRepository], written and read by the
 * real [BackupJsonMapper] and put back by [LocalBackupRepository.replaceAll]
 * into a second, empty database - the path a real export and restore take.
 *
 * It runs only on a device, since CI compiles instrumented tests without
 * running them.
 */
@RunWith(AndroidJUnit4::class)
class LocalBackupRepositoryRoundTripTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private lateinit var source: RepFlowDatabase
    private lateinit var target: RepFlowDatabase

    @Before
    fun createDatabases() {
        source = inMemoryDatabase()
        target = inMemoryDatabase()
    }

    @After
    fun closeDatabases() {
        source.close()
        target.close()
    }

    @Test
    fun everySessionComesBackWithItsStatusEndTimeExercisesAndSets() =
        runBlocking {
            val exerciseRepository = LocalExerciseRepository(source.exerciseDao())
            exerciseRepository.insert(exercise())
            val sourceWorkouts = workoutRepository(source)
            val completed = (sessionWithASet("completed", now).complete(now.plusSeconds(3600)) as DomainResult.Success).value
            val invalidated =
                (
                    (sessionWithASet("invalidated", now.plusSeconds(7200)).complete(now.plusSeconds(10800)) as DomainResult.Success)
                        .value
                        .invalidate(now.plusSeconds(14400)) as DomainResult.Success
                ).value
            val abandoned =
                (sessionWithASet("abandoned", now.plusSeconds(18000)).abandon(now.plusSeconds(21600)) as DomainResult.Success).value
            val running =
                (
                    sessionWithASet("running", now.plusSeconds(25200)).withStartedRestTimer(
                        RestTimer.start(durationSeconds = 90, now = now.plusSeconds(25500)),
                    ) as DomainResult.Success
                ).value
            listOf(completed, invalidated, abandoned, running).forEach { session ->
                check(sourceWorkouts.insert(session) is DomainResult.Success) { "seeding ${session.id} failed" }
            }
            val sourceBackup = LocalBackupRepository(source, LocalTrainingDataRepository(source))
            val exported = sourceWorkouts.findAllForBackup()
            val snapshot =
                (
                    BackupSnapshot.create(
                        schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                        exercises = exerciseRepository.findAll(),
                        trainingPlans = emptyList(),
                        workoutSessions = exported,
                        recoveryEntries = emptyList(),
                        futsalSessions = emptyList(),
                        progressionRecommendations = emptyList(),
                    ) as DomainResult.Success
                ).value

            val json = sourceBackup.serializeSnapshot(snapshot)
            val targetBackup = LocalBackupRepository(target, LocalTrainingDataRepository(target))
            val parsed = (targetBackup.parseSnapshot(json) as DomainResult.Success).value
            val restored = targetBackup.replaceAll(parsed)

            check(restored is DomainResult.Success) { "replaceAll failed: $restored" }
            val targetWorkouts = workoutRepository(target)
            assertEquals(listOf(completed, invalidated, abandoned, running), exported)
            assertEquals(exported, targetWorkouts.findAllForBackup())
            assertEquals(running, targetWorkouts.findActiveSession())
        }

    private fun inMemoryDatabase(): RepFlowDatabase =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
            .build()

    private fun workoutRepository(database: RepFlowDatabase) =
        LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao())

    private fun exercise(): Exercise =
        (
            Exercise.create(
                id = ExerciseId(EXERCISE_ID),
                name = (ExerciseName.create("Bench Press") as DomainResult.Success).value,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.BUILT_IN,
                createdAt = now,
            ) as DomainResult.Success
        ).value

    private fun sessionWithASet(
        id: String,
        startedAt: Instant,
    ): WorkoutSession {
        val sessionId = WorkoutSessionId(id)
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("$id-set"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = 7.5,
                    isWarmup = false,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                ) as DomainResult.Success
            ).value
        val exercise =
            (
                WorkoutExercise.create(
                    id = WorkoutExerciseId("$id-exercise"),
                    sessionId = sessionId,
                    exerciseId = ExerciseId(EXERCISE_ID),
                    order = 0,
                    exerciseNameSnapshot = "Bench Press",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ) as DomainResult.Success
            ).value
        return (WorkoutSession.start(sessionId, null, startedAt).withAddedExercise(exercise) as DomainResult.Success).value
    }

    private companion object {
        const val EXERCISE_ID = "ex-1"
    }
}
