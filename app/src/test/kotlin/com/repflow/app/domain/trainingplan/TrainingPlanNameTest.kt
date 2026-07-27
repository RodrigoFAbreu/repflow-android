package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingPlanNameTest {
    @Test
    fun `blank name is rejected`() {
        val result = TrainingPlanName.create("   ")

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.NameBlank), result)
    }

    @Test
    fun `name over the max length is rejected`() {
        val result = TrainingPlanName.create("a".repeat(TrainingPlanName.MAX_LENGTH + 1))

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.NameTooLong), result)
    }

    @Test
    fun `whitespace is collapsed and trimmed, capitalization preserved in the display value`() {
        val plan = requireSuccess(TrainingPlanName.create("  Push   Day  "))

        assertEquals("Push Day", plan.value)
        assertEquals("push day", plan.key)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
