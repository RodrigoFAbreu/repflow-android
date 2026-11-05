package com.repflow.app.data.backup

import android.content.Context
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.application.backup.PreRestoreSnapshot
import com.repflow.app.application.backup.PreRestoreSnapshotStore
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import javax.inject.Inject

/**
 * The [PreRestoreSnapshotStore] on three files in the app's private directory, where the database
 * already lives: never shared storage, and `allowBackup` is `false`.
 *
 * Every move and delete is [Files.move] or [Files.deleteIfExists], never `File.renameTo` or
 * `File.delete`. Those report failure by returning `false`, which the [IOException] catches here would
 * miss, so [saveNew] would report success and the restore would go ahead with no copy.
 *
 * Only a regular file counts as a copy, so a leftover check cannot move something that isn't one. The
 * time a copy was taken is its file's last-modified time, which a move keeps.
 */
class FilePreRestoreSnapshotStore(
    directory: File,
    private val ioDispatcher: CoroutineDispatcher,
) : PreRestoreSnapshotStore {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(context.filesDir, ioDispatcher)

    private val tempFile: Path = directory.toPath().resolve(TEMP_FILE_NAME)
    private val newFile: Path = directory.toPath().resolve(NEW_FILE_NAME)
    private val oldFile: Path = directory.toPath().resolve(OLD_FILE_NAME)

    override suspend fun saveNew(json: String): DomainResult<Unit, BackupRestoreError.Unavailable> =
        withContext(ioDispatcher) {
            try {
                moveNewOverOld()
                writeToDisk(json)
                Files.move(tempFile, newFile, StandardCopyOption.ATOMIC_MOVE)
                DomainResult.Success(Unit)
            } catch (expected: IOException) {
                deleteTempFile()
                DomainResult.Failure(BackupRestoreError.Unavailable)
            }
        }

    override suspend fun promoteNew() {
        withContext(ioDispatcher) {
            try {
                moveNewOverOld()
            } catch (expected: IOException) {
                // The new file stays as a leftover, which wins at the next read.
            }
        }
    }

    override suspend fun discardNew() {
        withContext(ioDispatcher) {
            try {
                Files.deleteIfExists(newFile)
            } catch (expected: IOException) {
                // The new file stays as a leftover, which wins at the next read.
            }
        }
    }

    override suspend fun load(): PreRestoreSnapshot? =
        withContext(ioDispatcher) {
            try {
                fileOnOffer()?.let { file ->
                    PreRestoreSnapshot(
                        json = Files.readAllBytes(file).toString(Charsets.UTF_8),
                        takenAt = Files.getLastModifiedTime(file).toInstant(),
                    )
                }
            } catch (expected: IOException) {
                null
            }
        }

    override suspend fun takenAt(): Instant? =
        withContext(ioDispatcher) {
            try {
                fileOnOffer()?.let { file -> Files.getLastModifiedTime(file).toInstant() }
            } catch (expected: IOException) {
                null
            }
        }

    override suspend fun clear(): DomainResult<Unit, BackupRestoreError.Unavailable> =
        withContext(ioDispatcher) {
            try {
                listOf(tempFile, newFile, oldFile).forEach { file -> Files.deleteIfExists(file) }
                DomainResult.Success(Unit)
            } catch (expected: IOException) {
                DomainResult.Failure(BackupRestoreError.Unavailable)
            }
        }

    /** A leftover new file wins. */
    private fun fileOnOffer(): Path? = listOf(newFile, oldFile).firstOrNull { file -> Files.isRegularFile(file) }

    private fun moveNewOverOld() {
        if (Files.isRegularFile(newFile)) Files.move(newFile, oldFile, StandardCopyOption.ATOMIC_MOVE)
    }

    /** On a full disk, a partial file would keep the space the next workout write needs. */
    private fun deleteTempFile() {
        try {
            Files.deleteIfExists(tempFile)
        } catch (expected: IOException) {
            // The next save or an erase replaces or deletes it.
        }
    }

    /**
     * `sync` before the move, because `flush` does not reach the disk: a rename can otherwise reach it
     * before the contents, leaving the data replaced and the copy empty.
     */
    private fun writeToDisk(json: String) {
        FileOutputStream(tempFile.toFile()).use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
    }

    private companion object {
        const val TEMP_FILE_NAME = "pre-restore-snapshot.tmp"
        const val NEW_FILE_NAME = "pre-restore-snapshot.new.json"
        const val OLD_FILE_NAME = "pre-restore-snapshot.json"
    }
}
