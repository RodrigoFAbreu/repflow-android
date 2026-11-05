package com.repflow.app.data.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ManualOverride
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ProgressionRecommendationMapperTest {
    private val computedAt = Instant.parse("2026-01-01T00:00:00Z")

    private fun requireSuccess(
        result: DomainResult<ProgressionRecommendation, com.repflow.app.domain.progression.ProgressionValidationError>,
    ): ProgressionRecommendation =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `round-trips a recommendation without an override`() {
        val recommendation =
            requireSuccess(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range", "moderate rpe"),
                    policyVersion = 1,
                    computedAt = computedAt,
                ),
            )
        val entity = ProgressionRecommendationMapper.toEntity(recommendation)
        val roundTripped = (ProgressionRecommendationMapper.toDomain(entity) as DomainResult.Success).value
        assertEquals(recommendation, roundTripped)
    }

    @Test
    fun `round-trips a recommendation with a manual override`() {
        val recommendation =
            requireSuccess(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range"),
                    policyVersion = 1,
                    computedAt = computedAt,
                    manualOverride = ManualOverride(ProgressionResult.MaintainLoad, computedAt.plusSeconds(60)),
                ),
            )
        val entity = ProgressionRecommendationMapper.toEntity(recommendation)
        val roundTripped = (ProgressionRecommendationMapper.toDomain(entity) as DomainResult.Success).value
        assertEquals(recommendation, roundTripped)
    }

    // LocalBackupRepository.parseSnapshot relies on this exception type to turn a bad key in a
    // backup file into a failure. CI doesn't run the instrumented test of that path, so these do.
    @Test(expected = IllegalArgumentException::class)
    fun `an unknown result is rejected as a bad argument`() {
        ProgressionRecommendationMapper.toDomain(entity(result = "INCREASE_LOAD"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown override result with an override time is rejected as a bad argument`() {
        ProgressionRecommendationMapper.toDomain(
            entity(overrideResult = "INCREASE_LOAD", overrideAt = computedAt.plusSeconds(60).toEpochMilli()),
        )
    }

    private fun entity(
        result: String = "increase_load",
        overrideResult: String? = null,
        overrideAt: Long? = null,
    ) = ProgressionRecommendationEntity(
        id = "rec-1",
        exerciseId = "exercise-1",
        result = result,
        reasons = "hit top of range",
        policyVersion = 1,
        computedAt = computedAt.toEpochMilli(),
        overrideResult = overrideResult,
        overrideAt = overrideAt,
    )
}
