package com.repflow.app.data.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseEntityMapperTest {
    private val defaultEntity =
        ExerciseEntity(
            id = "11111111-1111-1111-1111-111111111111",
            name = "Bench Press",
            nameKey = "bench press",
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS.name,
            instructions = null,
            defaultLoadIncrementGrams = null,
            defaultRestSeconds = null,
            origin = ExerciseOrigin.CUSTOM.name,
            archivedAt = null,
            createdAt = 1_000L,
            updatedAt = 1_000L,
        )

    @Test
    fun `round-trips every field through entity and back`() {
        val original =
            defaultEntity.copy(
                instructions = "Keep your shoulder blades retracted.",
                defaultLoadIncrementGrams = 2_500,
                defaultRestSeconds = 90,
                archivedAt = 5_000L,
                createdAt = 1_000L,
                updatedAt = 5_000L,
            )

        val exercise = requireSuccess(ExerciseEntityMapper.toDomain(original))
        val roundTripped = ExerciseEntityMapper.toEntity(exercise)

        assertEquals(original, roundTripped)
    }

    @Test
    fun `an unrecognised tracking type fails the mapping with the exercise id`() {
        val result = ExerciseEntityMapper.toDomain(defaultEntity.copy(trackingType = "NOT_A_REAL_TYPE"))

        val error = requireFailure(result)
        assertTrue(error is ExerciseMappingError.UnknownTrackingType)
        error as ExerciseMappingError.UnknownTrackingType
        assertEquals("11111111-1111-1111-1111-111111111111", error.exerciseId)
        assertEquals("NOT_A_REAL_TYPE", error.rawValue)
    }

    @Test
    fun `an unrecognised origin fails the mapping with the exercise id`() {
        val result = ExerciseEntityMapper.toDomain(defaultEntity.copy(origin = "NOT_A_REAL_ORIGIN"))

        val error = requireFailure(result)
        assertTrue(error is ExerciseMappingError.UnknownOrigin)
        error as ExerciseMappingError.UnknownOrigin
        assertEquals("11111111-1111-1111-1111-111111111111", error.exerciseId)
        assertEquals("NOT_A_REAL_ORIGIN", error.rawValue)
    }

    @Test
    fun `an out-of-range load increment fails the mapping instead of being silently clamped`() {
        val result = ExerciseEntityMapper.toDomain(defaultEntity.copy(defaultLoadIncrementGrams = -1))

        val error = requireFailure(result)
        assertTrue(error is ExerciseMappingError.InvalidFields)
    }

    private fun <T> requireSuccess(result: DomainResult<T, ExerciseMappingError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailure(result: DomainResult<T, ExerciseMappingError>): ExerciseMappingError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }
}
