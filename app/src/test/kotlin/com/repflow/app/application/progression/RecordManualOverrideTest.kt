package com.repflow.app.application.progression

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecordManualOverrideTest {
    private val computedAt = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(computedAt.plusSeconds(60))
    private val repository = InMemoryProgressionRecommendationRepository()
    private val useCase = RecordManualOverride(repository, clock)

    private suspend fun seedRecommendation(): ProgressionRecommendation {
        val recommendation =
            (
                ProgressionRecommendation.create(
                    id = ProgressionRecommendationId("rec-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    result = ProgressionResult.IncreaseLoad,
                    reasons = listOf("hit top of range"),
                    policyVersion = 1,
                    computedAt = computedAt,
                ) as DomainResult.Success
            ).value
        repository.insert(recommendation)
        return recommendation
    }

    @Test
    fun `records an override without mutating the original result`() =
        runTest {
            seedRecommendation()
            val result = useCase(ExerciseId("exercise-1"), ProgressionResult.MaintainLoad)
            assertTrue(result is DomainResult.Success)
            val updated = repository.findLatestForExercise(ExerciseId("exercise-1"))
            assertEquals(ProgressionResult.IncreaseLoad, updated?.result)
            assertEquals(ProgressionResult.MaintainLoad, updated?.manualOverride?.result)
        }

    @Test
    fun `fails with NotFound when no recommendation exists for the exercise`() =
        runTest {
            val result = useCase(ExerciseId("exercise-missing"), ProgressionResult.MaintainLoad)
            val failure = (result as DomainResult.Failure).error
            assertEquals(ProgressionOperationError.NotFound, failure)
            assertNull(repository.findLatestForExercise(ExerciseId("exercise-missing")))
        }
}
