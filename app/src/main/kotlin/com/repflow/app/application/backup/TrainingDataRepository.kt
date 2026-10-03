package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult

/**
 * The one general-purpose destructive primitive over the user's training data
 * (remediation-1 CP14), shared by its two callers so their scope cannot drift
 * apart: [EraseAllData], and the restore path
 * ([BackupRepository.replaceAll], which clears before it rewrites).
 *
 * "Training data" is exactly what the backup snapshot carries - exercises,
 * plans, plan versions, planned exercises, workout sessions (an active one
 * included), workout exercises, workout sets, recovery entries, futsal
 * sessions and progression recommendations. The device's settings are not
 * training data and are never cleared here.
 *
 * A port of its own rather than a method on [BackupRepository]: erasing
 * training data has nothing to do with backup, and naming the primitive after
 * the narrower of its two callers would hide what it does.
 */
interface TrainingDataRepository {
    /**
     * Deletes every training-data row in one transaction - all or nothing. Called
     * inside an enclosing transaction it joins that one, so the enclosing
     * transaction's rollback undoes it too.
     */
    suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError>
}

/** Why clearing training data did not happen. */
sealed interface TrainingDataError {
    /** Local storage could not complete the transaction; nothing was deleted. */
    data object Unavailable : TrainingDataError
}
