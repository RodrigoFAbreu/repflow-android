package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EraseAllDataTest {
    private val trainingData = InMemoryTrainingDataRepository()
    private val store = InMemoryPreRestoreSnapshotStore()
    private val lock = PreRestoreCopyLock()
    private val eraseAllData = EraseAllData(trainingData, store, lock)

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

    @Test
    fun `erasing deletes the copy before the data`() =
        runTest {
            store.old = PreRestoreSnapshot("old copy", Instant.EPOCH)
            var copyWasGoneAtTheClear = false
            val recording =
                object : TrainingDataRepository by trainingData {
                    override suspend fun clearTrainingData(): DomainResult<Unit, TrainingDataError> {
                        copyWasGoneAtTheClear = store.old == null
                        return trainingData.clearTrainingData()
                    }
                }

            val result = EraseAllData(recording, store, lock)()

            assertEquals(DomainResult.Success(Unit), result)
            assertTrue(copyWasGoneAtTheClear)
            assertNull(store.load())
            assertEquals(1, trainingData.clearCount)
        }

    @Test
    fun `a copy that can't be deleted stops the erase and clears no data`() =
        runTest {
            store.nextClearFails = true

            assertEquals(DomainResult.Failure(TrainingDataError.Unavailable), eraseAllData())
            assertEquals(0, trainingData.clearCount)
        }
}
