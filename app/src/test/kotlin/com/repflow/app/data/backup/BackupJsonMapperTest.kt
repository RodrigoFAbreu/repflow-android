package com.repflow.app.data.backup

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import com.repflow.app.infrastructure.database.recovery.FutsalSessionEntity
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryEntity
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSessionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupJsonMapperTest {
    @Suppress("LongMethod") // exercises every field of every backed-up table
    private fun fullSnapshot() =
        BackupEntitySnapshot(
            schemaVersion = 2,
            exercises =
                listOf(
                    ExerciseEntity(
                        id = "ex-1",
                        name = "Bench Press",
                        nameKey = "bench press",
                        trackingType = "WEIGHT_AND_REPS",
                        instructions = "Keep elbows tucked",
                        defaultLoadIncrementGrams = 2500,
                        defaultRestSeconds = 90,
                        origin = "BUILT_IN",
                        archivedAt = null,
                        createdAt = 1L,
                        updatedAt = 2L,
                    ),
                ),
            trainingPlans =
                listOf(
                    TrainingPlanEntity(
                        id = "plan-1",
                        name = "Push Pull Legs",
                        nameKey = "push pull legs",
                        createdAt = 1L,
                        updatedAt = 2L,
                        archivedAt = 3L,
                    ),
                ),
            trainingPlanVersions =
                listOf(
                    TrainingPlanVersionEntity(id = "v-1", planId = "plan-1", versionNumber = 1, note = null, createdAt = 1L),
                ),
            plannedExercises =
                listOf(
                    PlannedExerciseEntity(
                        id = "pe-1",
                        versionId = "v-1",
                        exerciseId = "ex-1",
                        sortOrder = 0,
                        targetSets = 3,
                        targetKind = "REPS",
                        repMin = 8,
                        repMax = 12,
                        durationMinSeconds = null,
                        durationMaxSeconds = null,
                        restSeconds = 90,
                        isOptional = false,
                        targetWarmupSets = 2,
                    ),
                ),
            workoutSessions =
                listOf(
                    WorkoutSessionEntity(
                        id = "session-1",
                        trainingPlanVersionId = "v-1",
                        status = "COMPLETED",
                        startedAt = 10L,
                        endedAt = 20L,
                        restTimerEndAtEpochMs = null,
                        restTimerTotalDurationSeconds = null,
                        invalidatedAt = 25L,
                    ),
                ),
            workoutExercises =
                listOf(
                    WorkoutExerciseEntity(
                        id = "we-1",
                        sessionId = "session-1",
                        exerciseId = "ex-1",
                        sortOrder = 0,
                        exerciseNameSnapshot = "Bench Press",
                        trackingType = "WEIGHT_AND_REPS",
                        plannedExerciseId = "pe-1",
                    ),
                ),
            workoutSets =
                listOf(
                    WorkoutSetEntity(
                        id = "set-1",
                        workoutExerciseId = "we-1",
                        sortOrder = 0,
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = 7.5,
                        isWarmup = false,
                        createdAt = 11L,
                        updatedAt = 12L,
                        pain = 2,
                        techniqueQuality = 4,
                    ),
                ),
            recoveryEntries =
                listOf(
                    RecoveryEntryEntity(
                        id = "re-1",
                        entryDate = "2026-01-01",
                        sleepQuality = 4,
                        energy = 3,
                        legDoms = 1,
                        heelStiffness = 0,
                        painWhileWalking = 0,
                        heavyLegs = 1,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = true,
                        notes = null,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            futsalSessions =
                listOf(
                    FutsalSessionEntity(
                        id = "fs-1",
                        entryDate = "2026-01-01",
                        durationMinutes = 60,
                        sessionRpe = 7.0,
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            progressionRecommendations =
                listOf(
                    ProgressionRecommendationEntity(
                        id = "pr-1",
                        exerciseId = "ex-1",
                        result = "INCREASE_LOAD",
                        reasons = "Performance was strong",
                        policyVersion = 1,
                        computedAt = 30L,
                        overrideResult = null,
                        overrideAt = null,
                    ),
                ),
        )

    @Test
    fun `round-trips every table through JSON`() {
        val original = fullSnapshot()

        val json = BackupJsonMapper.serialize(original)
        val parsed = (BackupJsonMapper.parse(json) as DomainResult.Success).value

        assertEquals(original, parsed)
    }

    @Test
    fun `round-trips an empty snapshot`() {
        val original =
            BackupEntitySnapshot(
                schemaVersion = 1,
                exercises = emptyList(),
                trainingPlans = emptyList(),
                trainingPlanVersions = emptyList(),
                plannedExercises = emptyList(),
                workoutSessions = emptyList(),
                workoutExercises = emptyList(),
                workoutSets = emptyList(),
                recoveryEntries = emptyList(),
                futsalSessions = emptyList(),
                progressionRecommendations = emptyList(),
            )

        val json = BackupJsonMapper.serialize(original)
        val parsed = (BackupJsonMapper.parse(json) as DomainResult.Success).value

        assertEquals(original, parsed)
    }

    /**
     * A hand-written schema-version-1-shaped backup (Milestone 8, CP14):
     * every key that existed before this milestone, and none of the five new
     * ones (`archivedAt` on a plan, `targetWarmupSets`, `invalidatedAt`,
     * `pain`/`techniqueQuality`) - exactly what a real backup exported by a
     * pre-Milestone-8 build would contain. Confirms the null-safe optional
     * reads treat a genuinely missing key the same as an explicit JSON null,
     * with no schema-version branching anywhere in the mapper.
     */
    private val v1ShapedJson =
        """
        {
          "schemaVersion": 1,
          "exercises": [],
          "trainingPlans": [
            {"id": "plan-1", "name": "Push Day", "nameKey": "push day", "createdAt": 1, "updatedAt": 2}
          ],
          "trainingPlanVersions": [
            {"id": "v-1", "planId": "plan-1", "versionNumber": 1, "note": null, "createdAt": 1}
          ],
          "plannedExercises": [
            {
              "id": "pe-1", "versionId": "v-1", "exerciseId": "ex-1", "sortOrder": 0,
              "targetSets": 3, "targetKind": "REPS", "repMin": 8, "repMax": 12,
              "durationMinSeconds": null, "durationMaxSeconds": null, "restSeconds": 90,
              "isOptional": false
            }
          ],
          "workoutSessions": [
            {
              "id": "session-1", "trainingPlanVersionId": "v-1", "status": "COMPLETED",
              "startedAt": 10, "endedAt": 20, "restTimerEndAtEpochMs": null,
              "restTimerTotalDurationSeconds": null
            }
          ],
          "workoutExercises": [
            {
              "id": "we-1", "sessionId": "session-1", "exerciseId": "ex-1", "sortOrder": 0,
              "exerciseNameSnapshot": "Bench Press", "trackingType": "WEIGHT_AND_REPS",
              "plannedExerciseId": "pe-1"
            }
          ],
          "workoutSets": [
            {
              "id": "set-1", "workoutExerciseId": "we-1", "sortOrder": 0, "load": 60.0,
              "reps": 8, "durationSeconds": null, "rpe": 7.5, "isWarmup": false,
              "createdAt": 11, "updatedAt": 12
            }
          ],
          "recoveryEntries": [],
          "futsalSessions": [],
          "progressionRecommendations": []
        }
        """.trimIndent()

    @Test
    fun `parses a v1-shaped backup with the five new fields all coming back null`() {
        val parsed = (BackupJsonMapper.parse(v1ShapedJson) as DomainResult.Success).value

        assertEquals(1, parsed.schemaVersion)
        assertEquals(null, parsed.trainingPlans.single().archivedAt)
        assertEquals(null, parsed.plannedExercises.single().targetWarmupSets)
        assertEquals(null, parsed.workoutSessions.single().invalidatedAt)
        assertEquals(null, parsed.workoutSets.single().pain)
        assertEquals(null, parsed.workoutSets.single().techniqueQuality)
        // Everything else still round-trips correctly.
        assertEquals("Push Day", parsed.trainingPlans.single().name)
        assertEquals(3, parsed.plannedExercises.single().targetSets)
        assertEquals("COMPLETED", parsed.workoutSessions.single().status)
        assertEquals(60.0, parsed.workoutSets.single().load)
    }

    @Test
    fun `fails with Malformed for text that is not valid JSON`() {
        val result = BackupJsonMapper.parse("not json")

        assertEquals(
            true,
            (result as DomainResult.Failure).error is com.repflow.app.domain.backup.BackupValidationError.Malformed,
        )
    }
}
