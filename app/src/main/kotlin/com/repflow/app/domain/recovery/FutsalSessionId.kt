package com.repflow.app.domain.recovery

/**
 * Stable identifier for a persisted [FutsalSession].
 *
 * Produced by an application-owned `IdentifierGenerator`, never by the
 * domain itself, mirroring [RecoveryEntryId].
 */
@JvmInline
value class FutsalSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "FutsalSessionId must not be blank" }
    }
}
