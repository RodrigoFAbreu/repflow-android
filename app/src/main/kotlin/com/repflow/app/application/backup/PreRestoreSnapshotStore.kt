package com.repflow.app.application.backup

import com.repflow.app.domain.common.DomainResult
import java.time.Instant

/**
 * Holds the safety copy: the backup text of the user's data, saved just before a restore replaced it.
 * The user only ever sees one copy, but the store has two states, a new copy beside an old one, because
 * a restore of the copy needs the old one intact until the database has committed. An invalid file or a
 * failed restore therefore leaves the old copy byte-identical.
 *
 * A leftover new copy, from a process death or a failed [promoteNew] or [discardNew], wins at the next
 * read. The reverse would be worse: a process death just after the commit must not lose the data that
 * was just replaced.
 *
 * Callers hold [PreRestoreCopyLock] around every use, since the rule that matters is about the whole
 * sequence and not about any one call here.
 */
interface PreRestoreSnapshotStore {
    /**
     * Durably writes [json] as the new copy, beside the old one. It first promotes any leftover new
     * copy, so a later failed restore cannot take away the copy the user was being offered.
     */
    suspend fun saveNew(json: String): DomainResult<Unit, BackupRestoreError.Unavailable>

    /**
     * Replaces the old copy with the new one. It reports no failure, because the restore's own outcome
     * is what the user needs to hear; a failed promotion leaves a leftover, which wins at the next read.
     */
    suspend fun promoteNew()

    /** Deletes the new copy and leaves the old one as it was. Like [promoteNew], it reports no failure. */
    suspend fun discardNew()

    /** The copy on offer with its text, or `null` if there is none or it can't be read. */
    suspend fun load(): PreRestoreSnapshot?

    /** When the copy on offer was taken, without reading the user's whole history into memory. */
    suspend fun takenAt(): Instant?

    /** Deletes every copy. */
    suspend fun clear(): DomainResult<Unit, BackupRestoreError.Unavailable>
}

/** A safety copy: an ordinary backup file's text, and when it was taken. */
data class PreRestoreSnapshot(
    val json: String,
    val takenAt: Instant,
)
