package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult

/**
 * Optional free-text instructions for an exercise.
 *
 * A blank (or whitespace-only) value is not a validation error - it simply
 * means "no instructions", represented as `null` rather than an empty
 * instance.
 */
@JvmInline
value class ExerciseInstructions private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH = 2000

        /**
         * Returns `null` (never a failure) when [raw] is blank, so callers
         * do not need to special-case "no instructions" separately from
         * validation.
         */
        fun createOrNull(raw: String?): DomainResult<ExerciseInstructions?, ExerciseValidationError> {
            val trimmed = raw?.trim().orEmpty()
            return when {
                trimmed.isEmpty() -> DomainResult.Success(null)
                trimmed.length > MAX_LENGTH -> DomainResult.Failure(ExerciseValidationError.InstructionsTooLong)
                else -> DomainResult.Success(ExerciseInstructions(trimmed))
            }
        }
    }
}
