package com.repflow.app.domain.trainingplan

/**
 * Validation failures raised while constructing or transitioning a
 * [TrainingPlan], a [TrainingPlanVersion], or one of their value objects.
 *
 * A flat set shared across the whole aggregate, mirroring
 * [com.repflow.app.domain.exercise.ExerciseValidationError]'s rationale:
 * application-layer callers collect these into a single list and map them
 * to field-attached UI errors.
 */
sealed interface TrainingPlanValidationError {
    /** The plan's display name is empty after cleaning, or normalizes to an empty key. */
    data object NameBlank : TrainingPlanValidationError

    /** The plan's display name exceeds [TrainingPlanName.MAX_LENGTH] characters after cleaning. */
    data object NameTooLong : TrainingPlanValidationError

    /** A version was built with an empty list of planned exercises. */
    data object NoPlannedExercises : TrainingPlanValidationError

    /** The planned exercises' `order` values are not exactly `0 until size`, with no gaps or repeats. */
    data object InvalidOrderSequence : TrainingPlanValidationError

    /** The target sets count is outside [TargetSets]'s supported range. */
    data object TargetSetsOutOfRange : TrainingPlanValidationError

    /** A planned exercise's target warm-up sets count is negative or exceeds [TargetSets.MAX]. */
    data object TargetWarmupSetsOutOfRange : TrainingPlanValidationError

    /** A repetition range's bounds violate [RepRange]'s invariants. */
    data object RepRangeInvalid : TrainingPlanValidationError

    /** A duration range's bounds violate [DurationTarget]'s invariants. */
    data object DurationRangeInvalid : TrainingPlanValidationError

    /**
     * A planned exercise's rest duration violates
     * [com.repflow.app.domain.exercise.RestDuration]'s invariants. Reuses
     * that value object's bounds rather than duplicating them; this case
     * only exists to carry its single possible failure
     * ([com.repflow.app.domain.exercise.ExerciseValidationError.RestDurationOutOfRange])
     * across into this aggregate's flat error set.
     */
    data object RestDurationOutOfRange : TrainingPlanValidationError

    /** `updatedAt` is before `createdAt` on a [TrainingPlan]. */
    data object UpdatedBeforeCreated : TrainingPlanValidationError

    /** A [TrainingPlanVersion]'s `versionNumber` is less than 1. */
    data object VersionNumberInvalid : TrainingPlanValidationError
}
