package com.repflow.app.domain.progression

import com.repflow.app.domain.trainingplan.RepRange

/**
 * Raw inputs [ProgressionPolicyV1] needs to evaluate a recommendation for
 * one exercise occurrence.
 *
 * [hadOnlyWarmupSets] (Milestone 8, CP5): true when the exercise had at
 * least one recorded set but every one of them was a warm-up set, so
 * [workingSetReps] is empty for a reason more specific than "not enough
 * data was recorded" - the caller already filters warm-ups out of
 * [workingSetReps]/[workingSetRpe] before this reaches the policy, so this
 * flag is the only way the policy can tell the two apart.
 */
data class ProgressionPolicyInput(
    val workingSetReps: List<Int>,
    val workingSetRpe: List<Double>,
    val plannedRepRange: RepRange?,
    val latestPainWhileWalking: Int?,
    val latestHeavyLegs: Int?,
    val hasRecentFutsalSession: Boolean,
    val hadOnlyWarmupSets: Boolean = false,
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
        // Milestone 8, CP5: three distinct, individually detectable reasons instead of one
        // sentence OR-ing "too few sets" and "no rep range" together. Checked in order of
        // specificity: "only warm-up sets" explains *why* workingSetReps is empty when that's
        // the actual cause, so it's checked before the more generic too-few-sets reason.
        if (input.hadOnlyWarmupSets) {
            return ProgressionEvaluation(
                ProgressionResult.WaitForMoreData,
                listOf("Only warm-up sets were recorded - no working sets to evaluate"),
            )
        }
        if (input.workingSetReps.size < MIN_SETS_FOR_RECOMMENDATION) {
            return ProgressionEvaluation(
                ProgressionResult.WaitForMoreData,
                listOf("Fewer than $MIN_SETS_FOR_RECOMMENDATION working sets recorded"),
            )
        }
        if (input.plannedRepRange == null) {
            return ProgressionEvaluation(
                ProgressionResult.WaitForMoreData,
                listOf("No planned rep range for this exercise"),
            )
        }

        val recoveryReasons = mutableListOf<String>()
        if ((input.latestPainWhileWalking ?: 0) >= PAIN_THRESHOLD) {
            recoveryReasons += "Pain while walking is elevated (${input.latestPainWhileWalking}/5)"
        }
        if ((input.latestHeavyLegs ?: 0) >= HEAVY_LEGS_THRESHOLD) {
            recoveryReasons += "Heavy legs is elevated (${input.latestHeavyLegs}/5)"
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
