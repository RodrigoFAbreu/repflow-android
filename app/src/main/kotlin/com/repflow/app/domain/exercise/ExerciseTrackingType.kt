package com.repflow.app.domain.exercise

/**
 * How a set of this exercise is tracked.
 *
 * Persisted by this stable [name] string, never by ordinal (see
 * `ExerciseTrackingTypeTest` for a test that pins the exact strings). Only
 * the three types the editor, validation, formatting and tests fully
 * support in Milestone 1 exist here (approved as D-21, "Option A").
 * `DISTANCE_AND_DURATION` and `BODYWEIGHT_WITH_OPTIONAL_LOAD` are deferred
 * until a concrete feature renders and validates them; adding enum values
 * later needs no migration because the persisted column is `TEXT`.
 */
enum class ExerciseTrackingType(
    val supportsLoad: Boolean,
) {
    WEIGHT_AND_REPS(supportsLoad = true),
    REPS_ONLY(supportsLoad = false),
    DURATION(supportsLoad = false),
}
