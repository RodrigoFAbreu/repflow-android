package com.repflow.app.application.progression

/**
 * A failure translated by the repository from a persistence-layer
 * exception, mirroring
 * [com.repflow.app.application.recovery.RecoveryPersistenceError].
 */
sealed interface ProgressionPersistenceError {
    data object Unavailable : ProgressionPersistenceError
}
