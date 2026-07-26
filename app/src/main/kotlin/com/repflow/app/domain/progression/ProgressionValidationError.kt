package com.repflow.app.domain.progression

/** Validation failures for [ProgressionRecommendation] and [ManualOverride]. */
sealed interface ProgressionValidationError {
    data object ReasonsMustNotBeEmpty : ProgressionValidationError

    data object NegativePolicyVersion : ProgressionValidationError

    data object OverrideBeforeComputed : ProgressionValidationError
}
