package com.repflow.app.application.backup

import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExportWorkoutHistoryCsvTest {
    private val repository = InMemoryWorkoutRepository()
    private val useCase = ExportWorkoutHistoryCsv(repository)

    @Test
    fun `formats one row per recorded set with a header`() =
        runTest {
            val set =
                (
                    WorkoutSet.create(
                        id = WorkoutSetId("set-1"),
                        order = 0,
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = 7.5,
                        isWarmup = false,
                        createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                        updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    ) as DomainResult.Success
                ).value
            val exercise =
                (
                    WorkoutExercise.create(
                        id = WorkoutExerciseId("we-1"),
                        sessionId = WorkoutSessionId("session-1"),
                        exerciseId = ExerciseId("ex-1"),
                        order = 0,
                        exerciseNameSnapshot = "Bench Press",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        plannedExerciseId = null,
                        sets = listOf(set),
                    ) as DomainResult.Success
                ).value
            val session =
                (
                    (
                        WorkoutSession
                            .start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
                            .withAddedExercise(exercise) as DomainResult.Success
                    ).value
                        .complete(Instant.parse("2026-01-01T01:00:00Z")) as DomainResult.Success
                ).value
            repository.insert(session)

            val csv = useCase()

            val lines = csv.trim().lines()
            assertTrue(lines[0].startsWith("session_id,started_at,ended_at,exercise_name"))
            assertTrue(lines[1].contains("\"session-1\""))
            assertTrue(lines[1].contains("\"Bench Press\""))
            assertTrue(lines[1].contains("\"8\""))
            assertTrue(lines[1].contains("\"60.0\""))
            assertTrue(lines[1].contains("\"7.5\""))
            assertTrue(lines[1].endsWith("false"))
        }
}
