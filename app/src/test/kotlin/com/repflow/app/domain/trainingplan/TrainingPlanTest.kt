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

    @Test
    fun `archive marks the plan archived and bumps updatedAt`() {
        val plan = requireSuccess(TrainingPlan.create(id = id, name = name, createdAt = createdAt))
        val archivedAt = createdAt.plusSeconds(60)

        val archived = plan.archive(archivedAt)

        assertEquals(true, archived.isArchived)
        assertEquals(archivedAt, archived.archivedAt)
        assertEquals(archivedAt, archived.updatedAt)
    }

    @Test
    fun `restore clears archivedAt and bumps updatedAt`() {
        val plan = requireSuccess(TrainingPlan.create(id = id, name = name, createdAt = createdAt))
        val archived = plan.archive(createdAt.plusSeconds(60))
        val restoredAt = createdAt.plusSeconds(120)

        val restored = archived.restore(restoredAt)

        assertEquals(false, restored.isArchived)
        assertEquals(null, restored.archivedAt)
        assertEquals(restoredAt, restored.updatedAt)
    }

    @Test
    fun `reconstruct rejects an archivedAt before createdAt`() {
        val result =
            TrainingPlan.reconstruct(
                id = id,
                name = name,
                createdAt = createdAt,
                updatedAt = createdAt,
                archivedAt = createdAt.minusSeconds(1),
            )

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.ArchivedBeforeCreated), result)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
