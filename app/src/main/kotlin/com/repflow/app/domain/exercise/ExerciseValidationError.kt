package com.repflow.app.domain.exercise

/**
 * Validation failures raised while constructing or transitioning an
 * [Exercise] or one of its value objects.
 *
 * This is intentionally a flat set shared across the whole aggregate rather
 * than one sealed type per value object: application-layer callers collect
 * these into a single `List<ExerciseValidationError>` (see
 * [Exercise.create]) and map them to field-attached UI errors.
 */
sealed interface ExerciseValidationError {
    /** The display name is empty after cleaning, or normalizes to an empty key. */
    data object NameBlank : ExerciseValidationError

    /** The display name exceeds [ExerciseName.MAX_LENGTH] characters after cleaning. */
    data object NameTooLong : ExerciseValidationError

    /** The instructions text exceeds [ExerciseInstructions.MAX_LENGTH] characters. */
    data object InstructionsTooLong : ExerciseValidationError

    /** A load increment was supplied for a tracking type where [ExerciseTrackingType.supportsLoad] is false. */
    data object LoadIncrementNotSupported : ExerciseValidationError

    /** The load increment is outside the supported range. */
    data object LoadIncrementOutOfRange : ExerciseValidationError

    /** The rest duration is outside the supported range. */
    data object RestDurationOutOfRange : ExerciseValidationError

    /** `updatedAt` is before `createdAt`. */
    data object UpdatedBeforeCreated : ExerciseValidationError

    /** `archivedAt` is present and before `createdAt`. */
    data object ArchivedBeforeCreated : ExerciseValidationError
}
