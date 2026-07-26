package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class TrainingPlanTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val id = TrainingPlanId("plan-1")
    private val name = requireSuccess(TrainingPlanName.create("Push Day"))

    @Test
    fun `create returns a plan with matching created and updated timestamps`() {
        val plan = requireSuccess(TrainingPlan.create(id = id, name = name, createdAt = createdAt))

        assertEquals(createdAt, plan.createdAt)
        assertEquals(createdAt, plan.updatedAt)
    }

    @Test
    fun `reconstruct rejects updatedAt before createdAt`() {
        val result =
            TrainingPlan.reconstruct(
                id = id,
                name = name,
                createdAt = createdAt,
                updatedAt = createdAt.minusSeconds(1),
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.UpdatedBeforeCreated), result)
    }

    @Test
    fun `reconstruct accepts updatedAt after createdAt`() {
        val updatedAt = createdAt.plusSeconds(60)

        val plan = requireSuccess(TrainingPlan.reconstruct(id = id, name = name, createdAt = createdAt, updatedAt = updatedAt))

        assertEquals(updatedAt, plan.updatedAt)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
