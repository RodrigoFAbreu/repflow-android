package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RepRangeTest {
    @Test
    fun `min below the minimum is rejected`() {
        val result = RepRange.create(RepRange.MIN_REPS - 1, 10)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.RepRangeInvalid), result)
    }

    @Test
    fun `max above the maximum is rejected`() {
        val result = RepRange.create(1, RepRange.MAX_REPS + 1)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.RepRangeInvalid), result)
    }

    @Test
    fun `min greater than max is rejected`() {
        val result = RepRange.create(12, 8)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.RepRangeInvalid), result)
    }

    @Test
    fun `a typical range is accepted`() {
        val range = requireSuccess(RepRange.create(8, 12))

        assertEquals(8, range.min)
        assertEquals(12, range.max)
    }

    @Test
    fun `equal min and max is accepted`() {
        val range = requireSuccess(RepRange.create(10, 10))

        assertEquals(10, range.min)
        assertEquals(10, range.max)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
