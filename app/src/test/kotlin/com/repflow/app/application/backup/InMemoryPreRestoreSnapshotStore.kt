package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import java.time.Instant

/**
 * A [PreRestoreSnapshotStore] fake with the real store's two states and leftover rules. [calls] names
 * every method called, in order, so a test can assert what was and wasn't touched.
 */
class InMemoryPreRestoreSnapshotStore(
    private val now: Instant = Instant.parse("2026-10-02T08:15:00Z"),
) : PreRestoreSnapshotStore {
    var old: PreRestoreSnapshot? = null
    var new: PreRestoreSnapshot? = null
    var nextSaveFails = false
    var nextClearFails = false
    val calls = mutableListOf<String>()

    override suspend fun saveNew(json: String): DomainResult<Unit, BackupRestoreError.Unavailable> {
        calls += "saveNew"
        if (nextSaveFails) {
            nextSaveFails = false
            return DomainResult.Failure(BackupRestoreError.Unavailable)
        }
        new?.let { old = it }
        new = PreRestoreSnapshot(json, now)
        return DomainResult.Success(Unit)
    }

    override suspend fun promoteNew() {
        calls += "promoteNew"
        new?.let { old = it }
        new = null
    }

    override suspend fun discardNew() {
        calls += "discardNew"
        new = null
    }

    override suspend fun load(): PreRestoreSnapshot? {
        calls += "load"
        return new ?: old
    }

    override suspend fun takenAt(): Instant? {
        calls += "takenAt"
        return (new ?: old)?.takenAt
    }

    override suspend fun clear(): DomainResult<Unit, BackupRestoreError.Unavailable> {
        calls += "clear"
        if (nextClearFails) {
            nextClearFails = false
            return DomainResult.Failure(BackupRestoreError.Unavailable)
        }
        old = null
        new = null
        return DomainResult.Success(Unit)
    }
}
