package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import javax.inject.Inject

/**
 * Settings' `Erase all data` (remediation-1 CP14): deletes every piece of the
 * user's training data on this device in one transaction, through the same
 * [TrainingDataRepository.clearTrainingData] a restore uses.
 *
 * Its blast radius, stated rather than discovered:
 * - **Cleared:** every training-data table (see [TrainingDataRepository]) -
 *   including an **active session**, which is discarded rather than refused,
 *   so a user who wants a clean slate does not first have to find and end it.
 *   Also the safety copy (see [PreRestoreSnapshotStore]), which holds the very
 *   data being erased: it goes first, for privacy, and if it can't be deleted
 *   nothing else is.
 * - **Kept:** the device's settings, and backup files already exported - those
 *   are the user's own files outside the database, which an in-app action
 *   never reaches.
 *
 * Holds [PreRestoreCopyLock] throughout, so it waits for a running restore and
 * no copy can be written after it.
 */
class EraseAllData
    @Inject
    constructor(
        private val trainingDataRepository: TrainingDataRepository,
        private val snapshotStore: PreRestoreSnapshotStore,
        private val lock: PreRestoreCopyLock,
    ) {
        suspend operator fun invoke(): DomainResult<Unit, TrainingDataError> =
            lock.withLock {
                when (snapshotStore.clear()) {
                    is DomainResult.Failure -> DomainResult.Failure(TrainingDataError.Unavailable)
                    is DomainResult.Success -> trainingDataRepository.clearTrainingData()
                }
            }
    }
