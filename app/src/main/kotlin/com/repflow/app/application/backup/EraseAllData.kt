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
 * - **Kept:** the device's settings, and backup files already exported - those
 *   are the user's own files outside the database, which an in-app action
 *   never reaches.
 */
class EraseAllData
    @Inject
    constructor(
        private val trainingDataRepository: TrainingDataRepository,
    ) {
        suspend operator fun invoke(): DomainResult<Unit, TrainingDataError> = trainingDataRepository.clearTrainingData()
    }
