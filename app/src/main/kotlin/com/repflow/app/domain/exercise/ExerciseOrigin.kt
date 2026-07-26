package com.repflow.app.domain.exercise

/**
 * Whether an exercise was pre-installed by the app or created by the user.
 *
 * `origin` is kept in the schema starting at version 1 specifically because
 * the approved optional built-in catalog (Milestone 1.1) is a concrete
 * near-term requirement, even though that catalog is not implemented until
 * the custom-exercise slice is complete and verified.
 */
enum class ExerciseOrigin {
    BUILT_IN,
    CUSTOM,
}
