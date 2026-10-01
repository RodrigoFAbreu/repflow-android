package com.repflow.app.presentation.progression

import androidx.annotation.StringRes
import com.repflow.app.R
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionResult

/*
 * The presentation layer's view of a progression recommendation, shared by the
 * two places that draw one: the workout's exercise picker row (a one-line
 * summary with its `Why ›` link) and the recommendation screen itself
 * (remediation-1 CP6). Moved here from `presentation/workout/` so both read the
 * same mapping rather than each re-deriving "what the user's final say is".
 */

/** The five [ProgressionResult] cases, as the presentation layer names them. */
enum class ProgressionResultUi {
    INCREASE_LOAD,
    MAINTAIN_LOAD,
    REDUCE_LOAD,
    RECOVERY_ADJUSTMENT,
    WAIT_FOR_MORE_DATA,
}

/**
 * The picker row's one-line summary of the latest recommendation for an
 * exercise: the result in force (the user's choice when there is one), the
 * policy's top reason, and whether the user's choice differs from the
 * suggestion.
 */
data class ProgressionRecommendationUi(
    val result: ProgressionResultUi,
    val topReason: String?,
    val isOverridden: Boolean,
)

fun ProgressionRecommendation.toSummaryUi(): ProgressionRecommendationUi =
    ProgressionRecommendationUi(
        result = (manualOverride?.result ?: result).toUi(),
        topReason = reasons.firstOrNull(),
        isOverridden = isOverridden(),
    )

/**
 * True when the user's recorded choice differs from what the policy suggested.
 *
 * A choice equal to the suggestion is possible - the recommendation screen's
 * `Change my mind` back to the suggestion records one, since an override
 * cannot be removed, only replaced (`ProgressionRecommendation.withOverride`) -
 * and it is not an override in any sense a reader would recognise.
 */
fun ProgressionRecommendation.isOverridden(): Boolean = manualOverride != null && manualOverride.result != result

fun ProgressionResult.toUi(): ProgressionResultUi =
    when (this) {
        ProgressionResult.IncreaseLoad -> ProgressionResultUi.INCREASE_LOAD
        ProgressionResult.MaintainLoad -> ProgressionResultUi.MAINTAIN_LOAD
        ProgressionResult.ReduceLoad -> ProgressionResultUi.REDUCE_LOAD
        ProgressionResult.RecoveryAdjustment -> ProgressionResultUi.RECOVERY_ADJUSTMENT
        ProgressionResult.WaitForMoreData -> ProgressionResultUi.WAIT_FOR_MORE_DATA
    }

fun ProgressionResultUi.toDomain(): ProgressionResult =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> ProgressionResult.IncreaseLoad
        ProgressionResultUi.MAINTAIN_LOAD -> ProgressionResult.MaintainLoad
        ProgressionResultUi.REDUCE_LOAD -> ProgressionResult.ReduceLoad
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> ProgressionResult.RecoveryAdjustment
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> ProgressionResult.WaitForMoreData
    }

/** The result's label - the screen's outcome title and the picker row's summary use the same words. */
@StringRes
fun ProgressionResultUi.labelRes(): Int =
    when (this) {
        ProgressionResultUi.INCREASE_LOAD -> R.string.progression_result_increase_load
        ProgressionResultUi.MAINTAIN_LOAD -> R.string.progression_result_maintain_load
        ProgressionResultUi.REDUCE_LOAD -> R.string.progression_result_reduce_load
        ProgressionResultUi.RECOVERY_ADJUSTMENT -> R.string.progression_result_recovery_adjustment
        ProgressionResultUi.WAIT_FOR_MORE_DATA -> R.string.progression_result_wait_for_more_data
    }
