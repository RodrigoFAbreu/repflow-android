package com.repflow.app.domain.progression

/**
 * The result of evaluating a [ProgressionRecommendation]'s policy for an
 * exercise, per `docs/DOMAIN_GLOSSARY.md`'s "Progression recommendation".
 */
sealed interface ProgressionResult {
    data object IncreaseLoad : ProgressionResult

    data object MaintainLoad : ProgressionResult

    data object ReduceLoad : ProgressionResult

    data object RecoveryAdjustment : ProgressionResult

    data object WaitForMoreData : ProgressionResult
}
