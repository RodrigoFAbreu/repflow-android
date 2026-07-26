package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WorkoutSetTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `create rejects a negative order`() {
        val result = set(order = -1)

        assertEquals(DomainResult.Failure(WorkoutValidationError.NegativeOrder), result)
    }

    @Test
    fun `create rejects updatedAt before createdAt`() {
        val result = set(updatedAt = createdAt.minusSeconds(1))

        assertEquals(DomainResult.Failure(WorkoutValidationError.UpdatedBeforeCreated), result)
    }

    @Test
    fun `create rejects an rpe outside 0 to 10`() {
        val result = set(rpe = 10.5)

        assertEquals(DomainResult.Failure(WorkoutValidationError.RpeOutOfRange), result)
    }

    @Test
    fun `create rejects a weight-and-reps set with no reps`() {
        val result = set(trackingType = ExerciseTrackingType.WEIGHT_AND_REPS, reps = null)

        assertEquals(DomainResult.Failure(WorkoutValidationError.RepsRequired), result)
    }

    @Test
    fun `create rejects a reps-only set carrying a load`() {
        val result = set(trackingType = ExerciseTrackingType.REPS_ONLY, reps = 10, load = 20.0)

        assertEquals(DomainResult.Failure(WorkoutValidationError.LoadNotApplicable), result)
    }

    @Test
    fun `create rejects a negative load`() {
        val result = set(trackingType = ExerciseTrackingType.WEIGHT_AND_REPS, reps = 10, load = -1.0)

        assertEquals(DomainResult.Failure(WorkoutValidationError.LoadOutOfRange), result)
    }

    @Test
    fun `create rejects a duration set with no duration`() {
        val result = set(trackingType = ExerciseTrackingType.DURATION, load = null, reps = null, durationSeconds = null)

        assertEquals(DomainResult.Failure(WorkoutValidationError.DurationRequired), result)
    }

    @Test
    fun `create rejects a duration set carrying reps`() {
        val result = set(trackingType = ExerciseTrackingType.DURATION, reps = 10, durationSeconds = 30)

        assertEquals(DomainResult.Failure(WorkoutValidationError.RepsNotApplicable), result)
    }

    @Test
    fun `create rejects a non-duration set carrying a durationSeconds value`() {
        val result = set(trackingType = ExerciseTrackingType.REPS_ONLY, reps = 10, durationSeconds = 30)

        assertEquals(DomainResult.Failure(WorkoutValidationError.DurationNotApplicable), result)
    }

    @Test
    fun `create accepts a valid weight-and-reps set`() {
        val result = set(trackingType = ExerciseTrackingType.WEIGHT_AND_REPS, reps = 8, load = 60.0)

        assertEquals(true, result is DomainResult.Success)
    }

    @Test
    fun `create accepts a valid duration set`() {
        val result = set(trackingType = ExerciseTrackingType.DURATION, load = null, reps = null, durationSeconds = 45)

        assertEquals(true, result is DomainResult.Success)
    }

    @Suppress("LongParameterList")
    private fun set(
        order: Int = 0,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        load: Double? = 20.0,
        reps: Int? = 10,
        durationSeconds: Int? = null,
        rpe: Double? = null,
        isWarmup: Boolean = false,
        updatedAt: Instant = createdAt,
    ) = WorkoutSet.create(
        id = WorkoutSetId("set-1"),
        order = order,
        trackingType = trackingType,
        load = load,
        reps = reps,
        durationSeconds = durationSeconds,
        rpe = rpe,
        isWarmup = isWarmup,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
