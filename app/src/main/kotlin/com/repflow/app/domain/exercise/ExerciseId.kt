package com.repflow.app.domain.exercise

/**
 * Stable identifier for a persisted [Exercise].
 *
 * The value is produced by an application-owned `IdentifierGenerator`
 * (typically a random UUID string), never by the domain itself, so tests can
 * supply deterministic ids. This type only guards against the programmer
 * error of an empty id - it is not a place for user-facing validation.
 */
@JvmInline
value class ExerciseId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ExerciseId must not be blank" }
    }
}
