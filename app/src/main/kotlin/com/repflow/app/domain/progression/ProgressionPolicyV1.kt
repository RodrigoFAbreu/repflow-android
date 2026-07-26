package com.repflow.app.domain.progression

import com.repflow.app.domain.trainingplan.RepRange

/** Raw inputs [ProgressionPolicyV1] needs to evaluate a recommendation for one exercise occurrence. */
data class ProgressionPolicyInput(
    val workingSetReps: List<Int>,
    val workingSetRpe: List<Double>,
    val plannedRepRange: RepRange?,
    val latestPainWhileWalking: Int?,
    val latestHeavyLegs: Int?,
    val hasRecentFutsalSession: Boolean,
)

/** The outcome of evaluating [ProgressionPolicyV1]: a result plus its contributing reasons. */
data class ProgressionEvaluation(
    val result: ProgressionResult,
    val reasons: List<String>,
)

/**
 * Policy v1 — a deterministic, local, explainable set of rules for
 * progression recommendations (`docs/TECHNICAL_DECISIONS.md`'s
 * "Recommendation engine").
 *
 * **This is an explicit, documented v1 placeholder.** Exact progression
 * formulas/thresholds are listed as an "Open" decision in
 * `docs/TECHNICAL_DECISIONS.md`; see `milestone-6-reference.md`'s
 * "Unresolved decision" section for why this milestone ships one
 * conservative, isolated policy rather than blocking on that decision.
 * All thresholds are named constants here so a future `ProgressionPolicyV2`
 * can replace this object without touching any caller.
 */
object ProgressionPolicyV1 {
    const val VERSION = 1

    private const val MIN_SETS_FOR_RECOMMENDATION = 2
    private const val PAIN_THRESHOLD = 3
    private const val HEAVY_LEGS_THRESHOLD = 3
    private const val RPE_REDUCE_THRESHOLD = 9.0
    private const val RPE_INCREASE_CEILING = 7.5

    @Suppress("ReturnCount")
    fun evaluate(input: ProgressionPolicyInput): ProgressionEvaluation {
        if (input.workingSetReps.size < MIN_SETS_FOR_RECOMMENDATION || input.plannedRepRange == null) {
            return ProgressionEvaluation(
                ProgressionResult.WaitForMoreData,
                listOf("Fewer than $MIN_SETS_FOR_RECOMMENDATION working sets or no planned rep range"),
            )
        }

        val recoveryReasons = mutableListOf<String>()
        if ((input.latestPainWhileWalking ?: 0) >= PAIN_THRESHOLD) {
            recoveryReasons += "Pain while walking is elevated (${input.latestPainWhileWalking}/4)"
        }
        if ((input.latestHeavyLegs ?: 0) >= HEAVY_LEGS_THRESHOLD) {
            recoveryReasons += "Heavy legs is elevated (${input.latestHeavyLegs}/4)"
        }
        if (input.hasRecentFutsalSession) {
            recoveryReasons += "Futsal session recorded in the last 24h"
        }
        if (recoveryReasons.isNotEmpty()) {
            return ProgressionEvaluation(ProgressionResult.RecoveryAdjustment, recoveryReasons)
        }

        val averageRpe = input.workingSetRpe.average()
        val range = input.plannedRepRange
        val belowRangeCount = input.workingSetReps.count { it < range.min }
        val atOrAboveTopCount = input.workingSetReps.count { it >= range.max }

        if (averageRpe >= RPE_REDUCE_THRESHOLD || belowRangeCount * 2 >= input.workingSetReps.size) {
            return ProgressionEvaluation(
                ProgressionResult.ReduceLoad,
                listOf(
                    "Average RPE $averageRpe is at or above $RPE_REDUCE_THRESHOLD, " +
                        "or fewer than half the working sets reached ${range.min} reps",
                ),
            )
        }

        if (atOrAboveTopCount == input.workingSetReps.size && averageRpe <= RPE_INCREASE_CEILING) {
            return ProgressionEvaluation(
                ProgressionResult.IncreaseLoad,
                listOf("Every working set reached ${range.max}+ reps at average RPE $averageRpe"),
            )
        }

        return ProgressionEvaluation(
            ProgressionResult.MaintainLoad,
            listOf("Performance was within the planned rep range but not clearly at either extreme"),
        )
    }
}
