package com.repflow.app.application.backup

import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult

/**
 * An in-memory [BackupRepository] fake for use-case tests. Rather than
 * truly serializing to JSON (that is CP3's `data.backup.BackupJsonMapper`
 * concern), [serializeSnapshot] stores the snapshot under a token string and
 * [parseSnapshot] looks it up - enough to exercise [ExportBackup]/[RestoreBackup]'s
 * own logic in isolation.
 */
class InMemoryBackupRepository : BackupRepository {
    private val snapshotsByToken = mutableMapOf<String, BackupSnapshot>()
    private var nextToken = 0

    var nextReplaceAllFailure: BackupRestoreError? = null
    var lastReplacedWith: BackupSnapshot? = null

    override fun serializeSnapshot(snapshot: BackupSnapshot): String {
        val token = "token-${nextToken++}"
        snapshotsByToken[token] = snapshot
        return token
    }

    override fun parseSnapshot(json: String): DomainResult<BackupSnapshot, BackupValidationError> =
        snapshotsByToken[json]?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(BackupValidationError.InvalidSchemaVersion(-1))

    override suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError> {
        nextReplaceAllFailure?.let {
            nextReplaceAllFailure = null
            return DomainResult.Failure(it)
        }
        lastReplacedWith = snapshot
        return DomainResult.Success(Unit)
    }
}
