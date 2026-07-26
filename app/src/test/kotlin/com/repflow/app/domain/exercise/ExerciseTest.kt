package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ExerciseTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val id = ExerciseId("11111111-1111-1111-1111-111111111111")
    private val name = requireSuccess(ExerciseName.create("Bench Press"))

    @Test
    fun `create returns an active exercise with matching created and updated timestamps`() {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = id,
                    name = name,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            )

        assertEquals(createdAt, exercise.createdAt)
        assertEquals(createdAt, exercise.updatedAt)
        assertNull(exercise.archivedAt)
        assertFalse(exercise.isArchived)
        assertEquals(ExerciseOrigin.CUSTOM, exercise.origin)
    }

    @Test
    fun `create rejects a load increment on a tracking type that does not support load`() {
        val loadIncrement = requireSuccess(LoadIncrement.create(2_500))

        val result =
            Exercise.create(
                id = id,
                name = name,
                trackingType = ExerciseTrackingType.REPS_ONLY,
                instructions = null,
                defaultLoadIncrement = loadIncrement,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = createdAt,
            )

        assertEquals(
            DomainResult.Failure(listOf(ExerciseValidationError.LoadIncrementNotSupported)),
            result,
        )
    }

    @Test
    fun `create accepts a load increment on a tracking type that supports load`() {
        val loadIncrement = requireSuccess(LoadIncrement.create(2_500))

        val exercise =
            requireSuccess(
                Exercise.create(
                    id = id,
                    name = name,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = loadIncrement,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            )

        assertEquals(loadIncrement, exercise.defaultLoadIncrement)
    }

    @Test
    fun `archive sets archivedAt and updatedAt but never createdAt or origin`() {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = id,
                    name = name,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            )
        val archivedAt = createdAt.plusSeconds(60)

        val archived = exercise.archive(archivedAt)

        assertTrue(archived.isArchived)
        assertEquals(archivedAt, archived.archivedAt)
        assertEquals(archivedAt, archived.updatedAt)
        assertEquals(createdAt, archived.createdAt)
        assertEquals(ExerciseOrigin.CUSTOM, archived.origin)
    }

    @Test
    fun `restore clears archivedAt and bumps updatedAt`() {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = id,
                    name = name,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            ).archive(createdAt.plusSeconds(60))
        val restoredAt = createdAt.plusSeconds(120)

        val restored = exercise.restore(restoredAt)

        assertFalse(restored.isArchived)
        assertNull(restored.archivedAt)
        assertEquals(restoredAt, restored.updatedAt)
        assertEquals(createdAt, restored.createdAt)
    }

    @Test
    fun `reconstruct rejects updatedAt before createdAt`() {
        val result =
            Exercise.reconstruct(
                id = id,
                name = name,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                archivedAt = null,
                createdAt = createdAt,
                updatedAt = createdAt.minusSeconds(1),
            )

        assertEquals(
            DomainResult.Failure(listOf(ExerciseValidationError.UpdatedBeforeCreated)),
            result,
        )
    }

    @Test
    fun `reconstruct rejects archivedAt before createdAt`() {
        val result =
            Exercise.reconstruct(
                id = id,
                name = name,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                archivedAt = createdAt.minusSeconds(1),
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        assertEquals(
            DomainResult.Failure(listOf(ExerciseValidationError.ArchivedBeforeCreated)),
            result,
        )
    }

    @Test
    fun `reconstruct collects every violated invariant`() {
        val loadIncrement = requireSuccess(LoadIncrement.create(2_500))

        val result =
            Exercise.reconstruct(
                id = id,
                name = name,
                trackingType = ExerciseTrackingType.REPS_ONLY,
                instructions = null,
                defaultLoadIncrement = loadIncrement,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                archivedAt = null,
                createdAt = createdAt,
                updatedAt = createdAt.minusSeconds(1),
            )

        assertEquals(
            DomainResult.Failure(
                listOf(
                    ExerciseValidationError.LoadIncrementNotSupported,
                    ExerciseValidationError.UpdatedBeforeCreated,
                ),
            ),
            result,
        )
    }

    @Test
    fun `reconstruct accepts a valid archived exercise`() {
        val archivedAt = createdAt.plusSeconds(60)

        val exercise =
            requireSuccess(
                Exercise.reconstruct(
                    id = id,
                    name = name,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.BUILT_IN,
                    archivedAt = archivedAt,
                    createdAt = createdAt,
                    updatedAt = archivedAt,
                ),
            )

        assertTrue(exercise.isArchived)
        assertEquals(ExerciseOrigin.BUILT_IN, exercise.origin)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
