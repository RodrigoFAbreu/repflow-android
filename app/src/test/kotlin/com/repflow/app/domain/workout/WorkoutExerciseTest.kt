package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WorkoutExerciseTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun exercise(
        order: Int = 0,
        sets: List<WorkoutSet> = emptyList(),
    ) = WorkoutExercise.create(
        id = WorkoutExerciseId("we-1"),
        sessionId = WorkoutSessionId("session-1"),
        exerciseId = ExerciseId("exercise-1"),
        order = order,
        exerciseNameSnapshot = "Back Squat",
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        plannedExerciseId = null,
        sets = sets,
    )

    private fun set(
        id: String,
        order: Int,
    ) = requireSuccess(
        WorkoutSet.create(
            id = WorkoutSetId(id),
            order = order,
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            load = 60.0,
            reps = 8,
            durationSeconds = null,
            rpe = null,
            isWarmup = false,
            createdAt = createdAt,
            updatedAt = createdAt,
        ),
    )

    @Test
    fun `create rejects a negative order`() {
        assertEquals(DomainResult.Failure(WorkoutValidationError.NegativeOrder), exercise(order = -1))
    }

    @Test
    fun `create rejects a blank exercise name snapshot`() {
        val result =
            WorkoutExercise.create(
                id = WorkoutExerciseId("we-1"),
                sessionId = WorkoutSessionId("session-1"),
                exerciseId = ExerciseId("exercise-1"),
                order = 0,
                exerciseNameSnapshot = "   ",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                plannedExerciseId = null,
            )

        assertEquals(DomainResult.Failure(WorkoutValidationError.ExerciseNameSnapshotBlank), result)
    }

    @Test
    fun `create rejects duplicate set order values`() {
        val result = exercise(sets = listOf(set("s1", 0), set("s2", 0)))

        assertEquals(DomainResult.Failure(WorkoutValidationError.DuplicateSetOrder), result)
    }

    @Test
    fun `withRecordedSet rejects a duplicate order`() {
        val existing = requireSuccess(exercise(sets = listOf(set("s1", 0))))

        val result = existing.withRecordedSet(set("s2", 0))

        assertEquals(DomainResult.Failure(WorkoutValidationError.DuplicateSetOrder), result)
    }

    @Test
    fun `withRecordedSet appends a new set in order`() {
        val existing = requireSuccess(exercise())

        val updated = requireSuccess(existing.withRecordedSet(set("s1", 0)))

        assertEquals(listOf(set("s1", 0)), updated.sets)
        assertEquals(1, updated.nextSetOrder())
    }

    @Test
    fun `withoutSet removes the matching set`() {
        val existing = requireSuccess(exercise(sets = listOf(set("s1", 0), set("s2", 1))))

        val updated = existing.withoutSet(WorkoutSetId("s1"))

        assertEquals(listOf(set("s2", 1)), updated.sets)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
