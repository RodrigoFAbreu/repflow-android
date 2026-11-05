package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.CompletableDeferred

/**
 * A [TrainingDataRepository] fake that counts clears and can be told to fail the next one. A test sets
 * [hasTrainingData], which a successful clear makes `false`, and can make the read throw or wait.
 */
class InMemoryTrainingDataRepository : TrainingDataRepository {
    var clearCount = 0
        private set

    var hasTrainingDataReads = 0
        private set

    var nextClearFailure: TrainingDataError? = null
    var hasTrainingData = true
    var readFailure: Throwable? = null
    var readGate: CompletableDeferred<Unit>? = null
    var clearGate: CompletableDeferred<Unit>? = null

    override suspend fun hasTrainingData(): Boolean {
        hasTrainingDataReads++
        readGate?.let {
            readGate = null
            it.await()
        }
        readFailure?.let { throw it }
        return hasTrainingData
    }

    override suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError> {
        clearGate?.let {
            clearGate = null
            it.await()
        }
        nextClearFailure?.let {
            nextClearFailure = null
            return DomainResult.Failure(it)
        }
        clearCount++
        hasTrainingData = false
        return DomainResult.Success(Unit)
    }
}
