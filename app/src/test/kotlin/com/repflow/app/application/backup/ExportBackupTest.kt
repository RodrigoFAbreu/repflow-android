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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
