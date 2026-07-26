package com.repflow.app.application.backup

import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreBackupTest {
    private val backupRepository = InMemoryBackupRepository()
    private val useCase = RestoreBackup(backupRepository)

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
}
