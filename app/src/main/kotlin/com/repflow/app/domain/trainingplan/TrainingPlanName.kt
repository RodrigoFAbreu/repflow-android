package com.repflow.app.domain.trainingplan

import com.repflow.app.domain.common.DomainResult
import java.text.Normalizer
import java.util.Locale

/**
 * A training plan's display name together with its normalized
 * identity/search key.
 *
 * Deliberately mirrors [com.repflow.app.domain.exercise.ExerciseName]'s
 * normalization pipeline exactly (NFKC -> trim -> collapse whitespace ->
 * lower-case key) so plan names behave identically to exercise names for
 * uniqueness, search and ordering. There is no single shared `Name` type
 * across both packages, however: each aggregate owns its own value object
 * and its own [MAX_LENGTH] and validation error, avoiding a speculative
 * shared abstraction with only two current consumers.
 */
@ConsistentCopyVisibility
data class TrainingPlanName private constructor(
    val value: String,
    val key: String,
) {
    companion object {
        const val MAX_LENGTH = 80

        private val whitespaceRun = Regex("[\\s\\p{Z}]+")

        fun cleanDisplay(raw: String): String {
            val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            return whitespaceRun.replace(nfkc.trim(), " ")
        }

        fun keyOf(cleanedDisplay: String): String = cleanedDisplay.lowercase(Locale.ROOT)

        fun create(raw: String): DomainResult<TrainingPlanName, TrainingPlanValidationError> {
            val display = cleanDisplay(raw)
            val key = keyOf(display)
            return when {
                display.isEmpty() -> DomainResult.Failure(TrainingPlanValidationError.NameBlank)
                display.length > MAX_LENGTH -> DomainResult.Failure(TrainingPlanValidationError.NameTooLong)
                key.isEmpty() -> DomainResult.Failure(TrainingPlanValidationError.NameBlank)
                else -> DomainResult.Success(TrainingPlanName(display, key))
            }
        }
    }
}
