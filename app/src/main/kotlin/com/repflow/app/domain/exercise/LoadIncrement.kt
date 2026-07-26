package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult

/**
 * A default load increment, stored as whole grams rather than a
 * floating-point kilogram value.
 *
 * Integer grams avoids `REAL` rounding drift building up across the
 * progression arithmetic this value will later drive (approved as D-7).
 */
@JvmInline
value class LoadIncrement private constructor(
    val grams: Long,
) {
    companion object {
        const val MIN_GRAMS = 1L
        const val MAX_GRAMS = 100_000L // 100 kg

        fun create(grams: Long): DomainResult<LoadIncrement, ExerciseValidationError> {
            if (grams < MIN_GRAMS || grams > MAX_GRAMS) {
                return DomainResult.Failure(ExerciseValidationError.LoadIncrementOutOfRange)
            }
            return DomainResult.Success(LoadIncrement(grams))
        }
    }
}
