package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import java.text.Normalizer
import java.util.Locale

/**
 * An exercise's display name together with its normalized identity/search
 * key.
 *
 * Normalization pipeline (approved as D-25, exact order matters):
 * 1. Unicode NFKC normalization ([Normalizer.Form.NFKC]) - folds
 *    compatibility characters and non-breaking spaces before anything else
 *    is inspected.
 * 2. Trim leading/trailing whitespace.
 * 3. Collapse any run of whitespace/Unicode separator characters
 *    (`[\s\p{Z}]+`) into a single ASCII space.
 * 4. Lower-case using [Locale.ROOT] (only for the key - never for the
 *    display value) to avoid locale-dependent surprises such as the Turkish
 *    dotless-i.
 *
 * [value] is steps 1-3 only, so the user's original capitalization and
 * accents are preserved for display (D-26). [key] additionally applies step
 * 4 and is the only form ever compared for uniqueness, search or ordering.
 * SQLite `COLLATE NOCASE` is never used for these purposes (D-23, D-27).
 */
@ConsistentCopyVisibility
data class ExerciseName private constructor(
    val value: String,
    val key: String,
) {
    companion object {
        const val MAX_LENGTH = 80

        private val whitespaceRun = Regex("[\\s\\p{Z}]+")

        /**
         * Cleans [raw] through steps 1-3 of the normalization pipeline
         * without lower-casing, i.e. the exact form persisted as the
         * display `name` column (D-26).
         */
        fun cleanDisplay(raw: String): String {
            val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            return whitespaceRun.replace(nfkc.trim(), " ")
        }

        /** Applies step 4 on top of an already-cleaned display value. */
        fun keyOf(cleanedDisplay: String): String = cleanedDisplay.lowercase(Locale.ROOT)

        fun create(raw: String): DomainResult<ExerciseName, ExerciseValidationError> {
            val display = cleanDisplay(raw)
            val key = keyOf(display)
            return when {
                display.isEmpty() -> DomainResult.Failure(ExerciseValidationError.NameBlank)
                display.length > MAX_LENGTH -> DomainResult.Failure(ExerciseValidationError.NameTooLong)
                key.isEmpty() -> DomainResult.Failure(ExerciseValidationError.NameBlank)
                else -> DomainResult.Success(ExerciseName(display, key))
            }
        }
    }
}
