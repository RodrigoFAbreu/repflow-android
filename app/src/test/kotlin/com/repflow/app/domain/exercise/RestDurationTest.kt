package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RestDurationTest {
    @Test
    fun `value below the minimum is rejected`() {
        val result = RestDuration.create(RestDuration.MIN_SECONDS - 1)

        assertEquals(DomainResult.Failure(ExerciseValidationError.RestDurationOutOfRange), result)
    }

    @Test
    fun `zero is rejected`() {
        val result = RestDuration.create(0)

        assertEquals(DomainResult.Failure(ExerciseValidationError.RestDurationOutOfRange), result)
    }

    @Test
    fun `value above the maximum is rejected`() {
        val result = RestDuration.create(RestDuration.MAX_SECONDS + 1)

        assertEquals(DomainResult.Failure(ExerciseValidationError.RestDurationOutOfRange), result)
    }

    @Test
    fun `minimum and maximum values are accepted`() {
        assertEquals(RestDuration.MIN_SECONDS, requireSuccess(RestDuration.create(RestDuration.MIN_SECONDS)).seconds)
        assertEquals(RestDuration.MAX_SECONDS, requireSuccess(RestDuration.create(RestDuration.MAX_SECONDS)).seconds)
    }

    @Test
    fun `a typical rest duration is accepted`() {
        val result = RestDuration.create(90)

        assertEquals(90L, requireSuccess(result).seconds)
    }

    private fun requireSuccess(result: DomainResult<RestDuration, ExerciseValidationError>): RestDuration =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
