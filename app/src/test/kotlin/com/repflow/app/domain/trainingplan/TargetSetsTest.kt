package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetSetsTest {
    @Test
    fun `value below the minimum is rejected`() {
        val result = TargetSets.create(TargetSets.MIN - 1)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.TargetSetsOutOfRange), result)
    }

    @Test
    fun `value above the maximum is rejected`() {
        val result = TargetSets.create(TargetSets.MAX + 1)

        assertEquals(DomainResult.Failure(TrainingPlanValidationError.TargetSetsOutOfRange), result)
    }

    @Test
    fun `minimum and maximum values are accepted`() {
        assertEquals(TargetSets.MIN, requireSuccess(TargetSets.create(TargetSets.MIN)).value)
        assertEquals(TargetSets.MAX, requireSuccess(TargetSets.create(TargetSets.MAX)).value)
    }

    @Test
    fun `validateWarmupSets accepts null as no guidance`() {
        assertEquals(null, TargetSets.validateWarmupSets(null))
    }

    @Test
    fun `validateWarmupSets accepts zero`() {
        assertEquals(null, TargetSets.validateWarmupSets(0))
    }

    @Test
    fun `validateWarmupSets rejects a negative value`() {
        assertEquals(TrainingPlanValidationError.TargetWarmupSetsOutOfRange, TargetSets.validateWarmupSets(-1))
    }

    @Test
    fun `validateWarmupSets rejects a value above the maximum`() {
        assertEquals(TrainingPlanValidationError.TargetWarmupSetsOutOfRange, TargetSets.validateWarmupSets(TargetSets.MAX + 1))
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
