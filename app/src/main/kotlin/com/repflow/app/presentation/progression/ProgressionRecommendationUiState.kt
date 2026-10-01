package com.repflow.app.presentation.progression

import com.repflow.app.domain.recovery.ReadinessScore
import java.time.Instant

/**
 * The recommendation screen's state (remediation-1 CP6): what was loaded,
 * whether the `Your call` options are open, whether a choice is being saved,
 * and a failed save to report.
 */
data class ProgressionRecommendationUiState(
    val content: RecommendationContent = RecommendationContent.Loading,
    val choosing: Boolean = false,
    val saving: Boolean = false,
    val error: RecommendationErrorReason? = null,
)

sealed interface RecommendationContent {
    data object Loading : RecommendationContent

    /** No recommendation has been computed for this exercise yet. */
    data object NotFound : RecommendationContent

    /** The recommendation could not be read. */
    data object Failed : RecommendationContent

    /**
     * The exercise's latest recommendation, exactly as the policy produced it.
     *
     * @property exerciseName `null` when the exercise itself no longer resolves;
     *   the screen then draws no name rather than a wrong one.
     * @property reasons every reason the policy recorded, in its own order and
     *   its own words (plan CP6 item 6: never rewritten here).
     * @property choice the user's recorded final say, `null` until there is one.
     * @property readiness today's readiness, read only for a
     *   [ProgressionResultUi.RECOVERY_ADJUSTMENT] - the one outcome whose
     *   explanation is a recovery fact (plan CP6 item 4) - and `null` when there
     *   is no check-in today or the outcome is any other.
     */
    data class Loaded(
        val exerciseName: String?,
        val suggested: ProgressionResultUi,
        val reasons: List<String>,
        val policyVersion: Int,
        val choice: RecommendationChoice?,
        val readiness: ReadinessScore? = null,
    ) : RecommendationContent {
        /** The result in force: the user's choice when there is one, otherwise the suggestion. */
        val inForce: ProgressionResultUi get() = choice?.result ?: suggested

        val isOverridden: Boolean get() = choice != null && choice.result != suggested
    }
}

/** A recorded `ManualOverride`: what the user chose, and when. */
data class RecommendationChoice(
    val result: ProgressionResultUi,
    val at: Instant,
)

/** The three outcomes the user can choose (`6a`'s `Your call`); a fourth, typed load, is `D32`. */
val RECOMMENDATION_CHOICES: List<ProgressionResultUi> =
    listOf(ProgressionResultUi.INCREASE_LOAD, ProgressionResultUi.MAINTAIN_LOAD, ProgressionResultUi.REDUCE_LOAD)

enum class RecommendationErrorReason {
    SAVE_FAILED,
}
