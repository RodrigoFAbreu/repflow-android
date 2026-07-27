package com.repflow.app.domain.recovery

/**
 * Stable identifier for a persisted [RecoveryEntry].
 *
 * Produced by an application-owned `IdentifierGenerator`, never by the
 * domain itself, mirroring
 * [com.repflow.app.domain.exercise.ExerciseId].
 */
@JvmInline
value class RecoveryEntryId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RecoveryEntryId must not be blank" }
    }
}
