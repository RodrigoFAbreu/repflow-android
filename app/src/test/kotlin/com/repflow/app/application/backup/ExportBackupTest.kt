package com.repflow.app.application.backup

import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.progression.InMemoryProgressionRecommendationRepository
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExportBackupTest {
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val workoutRepository = InMemoryWorkoutRepository()
    private val recoveryRepository = InMemoryRecoveryRepository()
    private val futsalRepository = InMemoryFutsalRepository()
    private val progressionRepository = InMemoryProgressionRecommendationRepository()
    private val backupRepository = InMemoryBackupRepository()

    private val useCase =
        ExportBackup(
            exerciseRepository = exerciseRepository,
            trainingPlanRepository = trainingPlanRepository,
            workoutRepository = workoutRepository,
            recoveryRepository = recoveryRepository,
            futsalRepository = futsalRepository,
            progressionRepository = progressionRepository,
            backupRepository = backupRepository,
        )

    @Test
    fun `builds a snapshot with the current schema version from every repository`() =
        runTest {
            exerciseRepository.seed(
                (
                    Exercise.create(
                        id = ExerciseId("ex-1"),
                        name = (ExerciseName.create("Bench Press") as DomainResult.Success).value,
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        instructions = null,
                        defaultLoadIncrement = null,
                        defaultRestDuration = null,
                        origin = ExerciseOrigin.BUILT_IN,
                        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ) as DomainResult.Success
                ).value,
            )

            val token = useCase()
            val snapshot = (backupRepository.parseSnapshot(token) as DomainResult.Success).value

            assertEquals(com.repflow.app.domain.backup.BackupSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion)
            assertEquals(1, snapshot.exercises.size)
            assertEquals(0, snapshot.trainingPlans.size)
            assertEquals(0, snapshot.workoutSessions.size)
            assertEquals(0, snapshot.recoveryEntries.size)
            assertEquals(0, snapshot.futsalSessions.size)
            assertEquals(0, snapshot.progressionRecommendations.size)
        }

    /**
     * Regression test (Milestone 8, CP13): CP11's `observeCompletedSessions`
     * exclusion of invalidated sessions was originally hard-coded and
     * unintentionally applied to this backup export too, silently dropping
     * an invalidated session's data entirely - a real violation of the
     * "never physically deleted" invariant. `ExportBackup` now reads
     * `findAllForBackup`, which has no filter at all.
     */
    @Test
    fun `includes an invalidated session in the snapshot rather than silently dropping it`() =
        runTest {
            val started = WorkoutSession.start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
            val completed =
                (started.complete(Instant.parse("2026-01-01T01:00:00Z")) as DomainResult.Success).value
            workoutRepository.insert(completed)
            val invalidated = (completed.invalidate(Instant.parse("2026-01-02T00:00:00Z")) as DomainResult.Success).value
            workoutRepository.update(invalidated)

            val token = useCase()
            val snapshot = (backupRepository.parseSnapshot(token) as DomainResult.Success).value

            assertEquals(1, snapshot.workoutSessions.size)
            assertTrue(snapshot.workoutSessions.single().isInvalidated)
        }

    @Test
    fun `includes completed abandoned and running sessions with their statuses and logged sets`() =
        runTest {
            val start = Instant.parse("2026-01-01T00:00:00Z")
            val completed =
                (
                    WorkoutSession.start(WorkoutSessionId("completed"), null, start).complete(start.plusSeconds(3600))
                        as DomainResult.Success
                ).value
            val abandoned =
                (
                    WorkoutSession.start(WorkoutSessionId("abandoned"), null, start.plusSeconds(7200)).abandon(start.plusSeconds(7500))
                        as DomainResult.Success
                ).value
            val running = runningSessionWithALoggedSet(start.plusSeconds(10800))
            workoutRepository.insert(completed)
            workoutRepository.insert(abandoned)
            workoutRepository.insert(running)

            val token = useCase()
            val snapshot = (backupRepository.parseSnapshot(token) as DomainResult.Success).value

            assertEquals(
                mapOf(
                    "completed" to WorkoutSessionStatus.COMPLETED,
                    "abandoned" to WorkoutSessionStatus.ABANDONED,
                    "running" to WorkoutSessionStatus.ACTIVE,
                ),
                snapshot.workoutSessions.associate { it.id.value to it.status },
            )
            assertEquals(running, snapshot.workoutSessions.single { it.status == WorkoutSessionStatus.ACTIVE })
        }

    private fun runningSessionWithALoggedSet(startedAt: Instant): WorkoutSession {
        val sessionId = WorkoutSessionId("running")
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                ) as DomainResult.Success
            ).value
        val exercise =
            (
                WorkoutExercise.create(
                    id = WorkoutExerciseId("workout-exercise-1"),
                    sessionId = sessionId,
                    exerciseId = ExerciseId("ex-1"),
                    order = 0,
                    exerciseNameSnapshot = "Bench Press",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ) as DomainResult.Success
            ).value
        return (WorkoutSession.start(sessionId, null, startedAt).withAddedExercise(exercise) as DomainResult.Success).value
    }
}
