package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult

/** A [TrainingDataRepository] fake that counts clears and can be told to fail the next one. */
class InMemoryTrainingDataRepository : TrainingDataRepository {
    var clearCount = 0
        private set

    var nextClearFailure: TrainingDataError? = null

    override suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError> {
        nextClearFailure?.let {
            nextClearFailure = null
            return DomainResult.Failure(it)
        }
        clearCount++
        return DomainResult.Success(Unit)
    }
}
