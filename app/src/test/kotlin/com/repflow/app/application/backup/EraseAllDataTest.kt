package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EraseAllDataTest {
    private val trainingData = InMemoryTrainingDataRepository()
    private val eraseAllData = EraseAllData(trainingData)

    @Test
    fun `erasing clears the training data once`() =
        runTest {
            assertEquals(DomainResult.Success(Unit), eraseAllData())
            assertEquals(1, trainingData.clearCount)
        }

    @Test
    fun `a storage failure is reported and nothing is counted as cleared`() =
        runTest {
            trainingData.nextClearFailure = TrainingDataError.Unavailable

            assertEquals(DomainResult.Failure(TrainingDataError.Unavailable), eraseAllData())
            assertEquals(0, trainingData.clearCount)
        }
}
