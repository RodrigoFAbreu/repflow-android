package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadIncrementTest {
    @Test
    fun `value below the minimum is rejected`() {
        val result = LoadIncrement.create(LoadIncrement.MIN_GRAMS - 1)

        assertEquals(DomainResult.Failure(ExerciseValidationError.LoadIncrementOutOfRange), result)
    }

    @Test
    fun `zero is rejected`() {
        val result = LoadIncrement.create(0)

        assertEquals(DomainResult.Failure(ExerciseValidationError.LoadIncrementOutOfRange), result)
    }

    @Test
    fun `value above the maximum is rejected`() {
        val result = LoadIncrement.create(LoadIncrement.MAX_GRAMS + 1)

        assertEquals(DomainResult.Failure(ExerciseValidationError.LoadIncrementOutOfRange), result)
    }

    @Test
    fun `minimum and maximum values are accepted`() {
        assertEquals(LoadIncrement.MIN_GRAMS, requireSuccess(LoadIncrement.create(LoadIncrement.MIN_GRAMS)).grams)
        assertEquals(LoadIncrement.MAX_GRAMS, requireSuccess(LoadIncrement.create(LoadIncrement.MAX_GRAMS)).grams)
    }

    @Test
    fun `a typical increment is accepted`() {
        val result = LoadIncrement.create(2_500)

        assertEquals(2_500L, requireSuccess(result).grams)
    }

    private fun requireSuccess(result: DomainResult<LoadIncrement, ExerciseValidationError>): LoadIncrement =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
