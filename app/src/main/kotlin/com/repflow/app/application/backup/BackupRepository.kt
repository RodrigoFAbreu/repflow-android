package com.repflow.app.application.backup

import com.repflow.app.domain.backup.BackupSnapshot
import com.repflow.app.domain.backup.BackupValidationError
import com.repflow.app.domain.common.DomainResult

/**
 * The application's capability contract for serializing/parsing a
 * [BackupSnapshot] and atomically replacing all local data with one.
 * Implemented by the infrastructure/data layer (`org.json`-based mapper +
 * a single Room transaction); no `org.json` or Room type is visible here
 * (see `LayerBoundaryTest`).
 */
interface BackupRepository {
    /** Renders [snapshot] as the on-disk transfer format (JSON). */
    fun serializeSnapshot(snapshot: BackupSnapshot): String

    /** Parses and validates [json] into a [BackupSnapshot], failing without touching any stored data. */
    fun parseSnapshot(json: String): DomainResult<BackupSnapshot, BackupValidationError>

    /** Atomically replaces every local aggregate with [snapshot]'s contents, in one transaction. */
    suspend fun replaceAll(snapshot: BackupSnapshot): DomainResult<Unit, BackupRestoreError>
}
