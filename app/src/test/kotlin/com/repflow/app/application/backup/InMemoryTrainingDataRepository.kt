package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * A [TrainingDataRepository] fake that counts clears and can be told to fail the next one. A test sets
 * [hasTrainingData], which a successful clear makes `false`, and can make the read throw or wait.
 * [observeHasTrainingData] follows [hasTrainingData], and [readFailure] fails it too.
 */
class InMemoryTrainingDataRepository : TrainingDataRepository {
    var clearCount = 0
        private set

    var hasTrainingDataReads = 0
        private set

    private val presence = MutableStateFlow(true)

    var nextClearFailure: TrainingDataError? = null
    var hasTrainingData: Boolean
        get() = presence.value
        set(value) {
            presence.value = value
        }
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

    override fun observeHasTrainingData(): Flow<Boolean> =
        flow {
            readFailure?.let { throw it }
            emitAll(presence)
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
