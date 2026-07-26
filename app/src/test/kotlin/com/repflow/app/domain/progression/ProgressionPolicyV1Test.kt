package com.repflow.app.domain.progression

import com.repflow.app.domain.trainingplan.RepRange
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionPolicyV1Test {
    private fun repRange(
        min: Int,
        max: Int,
    ) = (RepRange.create(min, max) as com.repflow.app.domain.common.DomainResult.Success).value

    @Test
    fun `fewer than two working sets waits for more data`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(10),
                    workingSetRpe = listOf(8.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.WaitForMoreData, result.result)
    }

    @Test
    fun `no planned rep range waits for more data`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(10, 10, 10),
                    workingSetRpe = listOf(8.0, 8.0, 8.0),
                    plannedRepRange = null,
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.WaitForMoreData, result.result)
    }

    @Test
    fun `elevated pain recommends a recovery adjustment regardless of performance`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(12, 12, 12),
                    workingSetRpe = listOf(6.0, 6.0, 6.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = 3,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.RecoveryAdjustment, result.result)
    }

    @Test
    fun `recent futsal session recommends a recovery adjustment`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(12, 12, 12),
                    workingSetRpe = listOf(6.0, 6.0, 6.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = true,
                ),
            )
        assertEquals(ProgressionResult.RecoveryAdjustment, result.result)
    }

    @Test
    fun `high rpe recommends reducing load`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(8, 8, 8),
                    workingSetRpe = listOf(9.5, 9.5, 9.5),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.ReduceLoad, result.result)
    }

    @Test
    fun `most sets below range recommends reducing load`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(5, 6, 12),
                    workingSetRpe = listOf(8.0, 8.0, 8.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.ReduceLoad, result.result)
    }

    @Test
    fun `all sets at top of range with moderate rpe recommends increasing load`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(12, 12, 13),
                    workingSetRpe = listOf(7.0, 7.0, 7.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.IncreaseLoad, result.result)
    }

    @Test
    fun `mid-range performance recommends maintaining load`() {
        val result =
            ProgressionPolicyV1.evaluate(
                ProgressionPolicyInput(
                    workingSetReps = listOf(10, 9, 10),
                    workingSetRpe = listOf(8.0, 8.0, 8.0),
                    plannedRepRange = repRange(8, 12),
                    latestPainWhileWalking = null,
                    latestHeavyLegs = null,
                    hasRecentFutsalSession = false,
                ),
            )
        assertEquals(ProgressionResult.MaintainLoad, result.result)
    }
}
