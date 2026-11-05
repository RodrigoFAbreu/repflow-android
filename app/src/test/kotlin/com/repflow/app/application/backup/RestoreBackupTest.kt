package com.repflow.app.application.backup

import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBackupTest {
    private val backupRepository = InMemoryBackupRepository()
    private val useCase = RestoreBackup(backupRepository, PreRestoreCopy { restore -> restore() })

    private fun emptySnapshot() =
        (
            BackupSnapshot.create(
                schemaVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION,
                exercises = emptyList(),
                trainingPlans = emptyList(),
                workoutSessions = emptyList(),
                recoveryEntries = emptyList(),
                futsalSessions = emptyList(),
                progressionRecommendations = emptyList(),
            ) as DomainResult.Success
        ).value

    @Test
    fun `parses and replaces all data for a valid backup`() =
        runTest {
            val token = backupRepository.serializeSnapshot(emptySnapshot())

            val result = useCase(token)

            assertTrue(result is DomainResult.Success)
            assertEquals(emptySnapshot(), backupRepository.lastReplacedWith)
        }

    @Test
    fun `fails with InvalidBackup for text that does not parse, without touching stored data`() =
        runTest {
            val result = useCase("not-a-real-token")

            assertTrue((result as DomainResult.Failure).error is BackupRestoreError.InvalidBackup)
            assertEquals(null, backupRepository.lastReplacedWith)
        }

    @Test
    fun `propagates a replaceAll failure`() =
        runTest {
            val token = backupRepository.serializeSnapshot(emptySnapshot())
            backupRepository.nextReplaceAllFailure = BackupRestoreError.Unavailable

            val result = useCase(token)

            assertEquals(BackupRestoreError.Unavailable, (result as DomainResult.Failure).error)
        }

    @Test
    fun `replaceAll runs inside the copy`() =
        runTest {
            val token = backupRepository.serializeSnapshot(emptySnapshot())
            val events = mutableListOf<String>()
            val aroundIt =
                RestoreBackup(
                    backupRepository,
                    PreRestoreCopy { restore ->
                        events += "before: replaced ${backupRepository.lastReplacedWith != null}"
                        val result = restore()
                        events += "after: replaced ${backupRepository.lastReplacedWith != null}"
                        result
                    },
                )

            assertTrue(aroundIt(token) is DomainResult.Success)

            assertEquals(listOf("before: replaced false", "after: replaced true"), events)
        }

    @Test
    fun `a copy that refuses returns Unavailable and replaces nothing`() =
        runTest {
            val token = backupRepository.serializeSnapshot(emptySnapshot())
            val refusing = RestoreBackup(backupRepository, PreRestoreCopy { DomainResult.Failure(BackupRestoreError.Unavailable) })

            val result = refusing(token)

            assertEquals(BackupRestoreError.Unavailable, (result as DomainResult.Failure).error)
            assertNull(backupRepository.lastReplacedWith)
        }

    @Test
    fun `an invalid file never calls the copy`() =
        runTest {
            var copyCalled = false
            val recording =
                RestoreBackup(
                    backupRepository,
                    PreRestoreCopy { restore ->
                        copyCalled = true
                        restore()
                    },
                )

            val result = recording("not-a-real-token")

            assertTrue((result as DomainResult.Failure).error is BackupRestoreError.InvalidBackup)
            assertFalse(copyCalled)
        }
}
