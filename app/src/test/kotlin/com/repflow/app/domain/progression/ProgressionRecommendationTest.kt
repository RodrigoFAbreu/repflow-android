package com.repflow.app.domain.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProgressionRecommendationTest {
    private val computedAt = Instant.parse("2026-01-01T00:00:00Z")

    private fun requireSuccess(result: DomainResult<ProgressionRecommendation, ProgressionValidationError>): ProgressionRecommendation =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `create succeeds with a non-empty reason and positive policy version`() {
        val recommendation =
            requireSuccess(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range"),
                    policyVersion = ProgressionPolicyV1.VERSION,
                    computedAt = computedAt,
                ),
            )
        assertEquals(ProgressionResult.IncreaseLoad, recommendation.result)
        assertEquals(null, recommendation.manualOverride)
    }

    @Test
    fun `create fails with empty reasons`() {
        val result =
            ProgressionRecommendation.create(
                id = ProgressionRecommendationId("rec-1"),
                exerciseId = ExerciseId("exercise-1"),
                result = ProgressionResult.MaintainLoad,
                reasons = emptyList(),
                policyVersion = 1,
                computedAt = computedAt,
            )
        assertTrue(result is DomainResult.Failure)
    }

    @Test
    fun `withOverride preserves the original result and stores the override`() {
        val recommendation =
            requireSuccess(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range"),
                    policyVersion = 1,
                    computedAt = computedAt,
                ),
            )
        val overridden =
            requireSuccess(
                recommendation.withOverride(ManualOverride(ProgressionResult.MaintainLoad, computedAt.plusSeconds(60))),
            )
        assertEquals(ProgressionResult.IncreaseLoad, overridden.result)
        assertEquals(ProgressionResult.MaintainLoad, overridden.manualOverride?.result)
    }

    @Test
    fun `withOverride rejects an override timestamp before computedAt`() {
        val recommendation =
            requireSuccess(
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range"),
                    policyVersion = 1,
                    computedAt = computedAt,
                ),
            )
        val result = recommendation.withOverride(ManualOverride(ProgressionResult.MaintainLoad, computedAt.minusSeconds(60)))
        assertTrue(result is DomainResult.Failure)
    }
}
