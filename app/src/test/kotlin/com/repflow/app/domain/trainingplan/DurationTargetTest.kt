package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class DurationTargetTest {
    @Test
    fun `min below the minimum is rejected`() {
        val result = DurationTarget.create(DurationTarget.MIN_SECONDS - 1, 60)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.DurationRangeInvalid), result)
    }

    @Test
    fun `max above the maximum is rejected`() {
        val result = DurationTarget.create(1, DurationTarget.MAX_SECONDS + 1)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.DurationRangeInvalid), result)
    }

    @Test
    fun `min greater than max is rejected`() {
        val result = DurationTarget.create(60, 30)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.DurationRangeInvalid), result)
    }

    @Test
    fun `a typical range is accepted`() {
        val range = requireSuccess(DurationTarget.create(30, 60))

        assertEquals(30L, range.minSeconds)
        assertEquals(60L, range.maxSeconds)
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
