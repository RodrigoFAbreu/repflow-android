package com.repflow.app.domain.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import java.time.Instant

/**
 * An explainable, versioned suggestion for how to progress an [Exercise],
 * per `docs/DOMAIN_GLOSSARY.md`'s "Progression recommendation" and
 * "Progression policy".
 *
 * Immutable once computed: a later [ManualOverride] only annotates it via
 * [withOverride], never replacing or mutating [result]/[reasons] -
 * preserving both the original recommendation and the user's decision.
 */
@ConsistentCopyVisibility
data class ProgressionRecommendation private constructor(
    val id: ProgressionRecommendationId,
    val exerciseId: ExerciseId,
    val result: ProgressionResult,
    val reasons: List<String>,
    val policyVersion: Int,
    val computedAt: Instant,
    val manualOverride: ManualOverride?,
) {
    /** Returns a copy annotated with [override], preserving the original [result]/[reasons]. */
    fun withOverride(override: ManualOverride): DomainResult<ProgressionRecommendation, ProgressionValidationError> {
        if (override.overriddenAt < computedAt) {
            return DomainResult.Failure(ProgressionValidationError.OverrideBeforeComputed)
        }
        return DomainResult.Success(copy(manualOverride = override))
    }

    companion object {
        @Suppress("LongParameterList", "ReturnCount")
        fun create(
            id: ProgressionRecommendationId,
            exerciseId: ExerciseId,
            result: ProgressionResult,
            reasons: List<String>,
            policyVersion: Int,
            computedAt: Instant,
            manualOverride: ManualOverride? = null,
        ): DomainResult<ProgressionRecommendation, ProgressionValidationError> {
            if (reasons.isEmpty()) {
                return DomainResult.Failure(ProgressionValidationError.ReasonsMustNotBeEmpty)
            }
            if (policyVersion < 1) {
                return DomainResult.Failure(ProgressionValidationError.NegativePolicyVersion)
            }
            if (manualOverride != null && manualOverride.overriddenAt < computedAt) {
                return DomainResult.Failure(ProgressionValidationError.OverrideBeforeComputed)
            }
            return DomainResult.Success(
                ProgressionRecommendation(
                    id = id,
                    exerciseId = exerciseId,
                    result = result,
                    reasons = reasons,
                    policyVersion = policyVersion,
                    computedAt = computedAt,
                    manualOverride = manualOverride,
                ),
            )
        }
    }
}
