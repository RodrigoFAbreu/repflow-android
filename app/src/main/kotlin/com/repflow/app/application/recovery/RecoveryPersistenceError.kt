package com.repflow.app.application.recovery

/**
 * A failure translated by the repository from a persistence-layer
 * exception, mirroring
 * [com.repflow.app.application.exercise.ExercisePersistenceError].
 */
sealed interface RecoveryPersistenceError {
    data object Unavailable : RecoveryPersistenceError
}

/** Mirrors [RecoveryPersistenceError] for [com.repflow.app.domain.recovery.FutsalSession] writes. */
sealed interface FutsalPersistenceError {
    data object Unavailable : FutsalPersistenceError
}
