package com.repflow.app.domain.backup

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSnapshotTest {
    private fun emptySnapshot(schemaVersion: Int) =
        BackupSnapshot.create(
            schemaVersion = schemaVersion,
            exercises = emptyList(),
            trainingPlans = emptyList(),
            workoutSessions = emptyList(),
            recoveryEntries = emptyList(),
            futsalSessions = emptyList(),
            progressionRecommendations = emptyList(),
        )

    @Test
    fun `accepts the current schema version`() {
        val result = emptySnapshot(BackupSnapshot.CURRENT_SCHEMA_VERSION)
        assertEquals(BackupSnapshot.CURRENT_SCHEMA_VERSION, (result as DomainResult.Success).value.schemaVersion)
    }

    @Test
    fun `rejects a schema version below 1`() {
        val result = emptySnapshot(0)
        assertEquals(BackupValidationError.InvalidSchemaVersion(0), (result as DomainResult.Failure).error)
    }

    @Test
    fun `rejects a schema version newer than this app understands`() {
        val futureVersion = BackupSnapshot.CURRENT_SCHEMA_VERSION + 1
        val result = emptySnapshot(futureVersion)
        assertEquals(BackupValidationError.UnsupportedSchemaVersion(futureVersion), (result as DomainResult.Failure).error)
    }
}
