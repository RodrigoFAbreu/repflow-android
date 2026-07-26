package com.repflow.app.domain.workout

/**
 * Validation failures raised while constructing or transitioning a
 * [WorkoutSession], a [WorkoutExercise], or a [WorkoutSet].
 *
 * A flat set shared across the whole aggregate, mirroring
 * [com.repflow.app.domain.trainingplan.TrainingPlanValidationError]'s
 * rationale.
 */
sealed interface WorkoutValidationError {
    /** An `order` value on a [WorkoutExercise] or [WorkoutSet] is negative. */
    data object NegativeOrder : WorkoutValidationError

    /** Two [WorkoutExercise]s in the same session share an `order` value. */
    data object DuplicateExerciseOrder : WorkoutValidationError

    /** Two [WorkoutSet]s in the same exercise share an `order` value. */
    data object DuplicateSetOrder : WorkoutValidationError

    /** A [WorkoutExercise]'s `exerciseNameSnapshot` is blank. */
    data object ExerciseNameSnapshotBlank : WorkoutValidationError

    /**
     * A [WorkoutSet] tracked with [com.repflow.app.domain.exercise.ExerciseTrackingType.WEIGHT_AND_REPS]
     * or [com.repflow.app.domain.exercise.ExerciseTrackingType.REPS_ONLY] is missing a positive `reps` value.
     */
    data object RepsRequired : WorkoutValidationError

    /** A [WorkoutSet] tracked by duration carries a `reps` value, which does not apply to it. */
    data object RepsNotApplicable : WorkoutValidationError

    /** A [WorkoutSet] not tracked with load carries a `load` value, which does not apply to it. */
    data object LoadNotApplicable : WorkoutValidationError

    /** A [WorkoutSet]'s `load` value is negative. */
    data object LoadOutOfRange : WorkoutValidationError

    /** A [WorkoutSet] tracked by duration is missing a positive `durationSeconds` value. */
    data object DurationRequired : WorkoutValidationError

    /** A [WorkoutSet] not tracked by duration carries a `durationSeconds` value, which does not apply to it. */
    data object DurationNotApplicable : WorkoutValidationError

    /** A [WorkoutSet]'s `rpe` value is outside the supported `0.0..10.0` range. */
    data object RpeOutOfRange : WorkoutValidationError

    /** A [WorkoutSet]'s `pain` value is outside the supported `0..5` range. */
    data object PainOutOfRange : WorkoutValidationError

    /** A [WorkoutSet]'s `techniqueQuality` value is outside the supported `0..5` range. */
    data object TechniqueQualityOutOfRange : WorkoutValidationError

    /** A [WorkoutSet]'s `updatedAt` is before its `createdAt`. */
    data object UpdatedBeforeCreated : WorkoutValidationError

    /** A [WorkoutSession]'s `endedAt` is before its `startedAt`. */
    data object EndedBeforeStarted : WorkoutValidationError

    /** A [WorkoutSession] in a terminal status ([WorkoutSessionStatus.COMPLETED] or [WorkoutSessionStatus.ABANDONED]) has no `endedAt`. */
    data object EndedAtRequiredForTerminalStatus : WorkoutValidationError

    /** A [WorkoutSession] with [WorkoutSessionStatus.ACTIVE] carries an `endedAt`, which does not apply to it. */
    data object EndedAtNotAllowedForActiveSession : WorkoutValidationError

    /** An operation that requires an [WorkoutSessionStatus.ACTIVE] session was attempted on a non-active one. */
    data object SessionNotActive : WorkoutValidationError

    /** An operation referenced a [WorkoutExerciseId] that is not part of the session. */
    data object ExerciseNotFound : WorkoutValidationError
}
