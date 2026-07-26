package com.repflow.app.presentation.exercise

import kotlin.math.roundToLong

/**
 * Shared kilogram-text <-> integer-gram conversions between the exercise
 * list/editor presentation code and the domain's integer-gram
 * [com.repflow.app.domain.exercise.LoadIncrement] (D-7).
 *
 * Deliberately presentation-only: the domain and application layers never
 * see a kilogram value, only whole grams.
 */
object ExerciseUiFormatting {
    private const val GRAMS_PER_KG = 1_000.0

    /** Parses a kilogram-denominated text field into whole grams, or `null` if unparsable. */
    fun kgTextToGrams(text: String): Long? {
        val kg = text.trim().toDoubleOrNull() ?: return null
        return (kg * GRAMS_PER_KG).roundToLong()
    }

    /** Formats whole grams back into a kilogram-denominated text field value, e.g. `2500` -> `"2.5"`. */
    fun gramsToKgText(grams: Long): String {
        val kg = grams / GRAMS_PER_KG
        val rounded = kg.toLong()
        return if (rounded.toDouble() == kg) rounded.toString() else kg.toString()
    }
}
