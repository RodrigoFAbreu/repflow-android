package com.repflow.app.data.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ManualOverride
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
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
}
