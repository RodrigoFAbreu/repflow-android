package com.repflow.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.application.backup.BackupRestoreError
import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/**
 * The real file store, on the device's file system. The `fd.sync()` before the move has no observable
 * effect in a test and is checked by reading.
 *
 * Each failure is forced by a non-empty directory at a file's path. A leftover check that accepted
 * anything at the new file's path would try to promote the directory first, and the failing-move test
 * would pass without ever running the move it is there to prove.
 */
@RunWith(AndroidJUnit4::class)
class FilePreRestoreSnapshotStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory = File(context.cacheDir, "pre-restore-snapshot-store-test")
    private val store = FilePreRestoreSnapshotStore(directory, Dispatchers.IO)

    private val tempFile = File(directory, "pre-restore-snapshot.tmp")
    private val newFile = File(directory, "pre-restore-snapshot.new.json")
    private val oldFile = File(directory, "pre-restore-snapshot.json")

    @Before
    fun createDirectory() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun saveAndPromoteRoundTripTheTextAndATimeAndTakenAtAgrees() =
        runBlocking<Unit> {
            val before = Instant.now().minusSeconds(FILE_TIME_SLACK_SECONDS)

            assertEquals(DomainResult.Success(Unit), store.saveNew(TEXT))
            store.promoteNew()

            val loaded = checkNotNull(store.load())
            assertEquals(TEXT, loaded.json)
            assertFalse("taken at ${loaded.takenAt}, before $before", loaded.takenAt.isBefore(before))
            assertFalse(loaded.takenAt.isAfter(Instant.now().plusSeconds(FILE_TIME_SLACK_SECONDS)))
            assertEquals(loaded.takenAt, store.takenAt())
            assertFalse("the new file must be gone after a promotion", newFile.exists())
        }

    @Test
    fun aSecondRoundReplacesTheFirst() =
        runBlocking<Unit> {
            saveAndPromote("first")

            saveAndPromote("second")

            assertEquals("second", store.load()?.json)
        }

    @Test
    fun anAbsentCopyLoadsNull() =
        runBlocking<Unit> {
            assertNull(store.load())
            assertNull(store.takenAt())
        }

    @Test
    fun aDiscardLeavesTheOldCopyByteIdentical() =
        runBlocking<Unit> {
            saveAndPromote(TEXT)
            val before = oldFile.readBytes()
            val takenAt = store.takenAt()

            store.saveNew("a copy the restore then discards")
            store.discardNew()

            assertArrayEquals(before, oldFile.readBytes())
            assertEquals(TEXT, store.load()?.json)
            assertEquals(takenAt, store.takenAt())
            assertFalse(newFile.exists())
        }

    @Test
    fun aLeftoverNewFileWinsInBothLoadAndTakenAt() =
        runBlocking<Unit> {
            saveAndPromote("old copy")
            store.saveNew("leftover")
            assertTrue(oldFile.setLastModified(OLD_MILLIS))
            assertTrue(newFile.setLastModified(LEFTOVER_MILLIS))

            val loaded = checkNotNull(store.load())

            assertEquals("leftover", loaded.json)
            assertEquals(Instant.ofEpochMilli(LEFTOVER_MILLIS), loaded.takenAt)
            assertEquals(Instant.ofEpochMilli(LEFTOVER_MILLIS), store.takenAt())
        }

    @Test
    fun saveNewOverALeftoverPromotesItFirstSoADiscardLeavesThatLeftover() =
        runBlocking<Unit> {
            saveAndPromote("old copy")
            store.saveNew("leftover")

            assertEquals(DomainResult.Success(Unit), store.saveNew("newer"))
            store.discardNew()

            assertEquals("leftover", store.load()?.json)
        }

    @Test
    fun clearDeletesAllThreeFilesAndIsIdempotent() =
        runBlocking<Unit> {
            listOf(tempFile, newFile, oldFile).forEach { it.writeText("a copy") }

            assertEquals(DomainResult.Success(Unit), store.clear())

            listOf(tempFile, newFile, oldFile).forEach { assertFalse("${it.name} must be gone", it.exists()) }
            assertNull(store.load())
            assertEquals(DomainResult.Success(Unit), store.clear())
        }

    @Test
    fun aFailingDeleteReturnsUnavailable() =
        runBlocking<Unit> {
            putNonEmptyDirectoryAt(newFile)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), store.clear())
        }

    @Test
    fun aFailingWriteReturnsUnavailableAndLeavesThePreviousCopyReadable() =
        runBlocking<Unit> {
            saveAndPromote(TEXT)
            putNonEmptyDirectoryAt(tempFile)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), store.saveNew("never written"))

            assertEquals(TEXT, store.load()?.json)
        }

    @Test
    fun aFailingMoveReturnsUnavailableAndLeavesThePreviousCopyReadable() =
        runBlocking<Unit> {
            saveAndPromote(TEXT)
            putNonEmptyDirectoryAt(newFile)

            assertEquals(DomainResult.Failure(BackupRestoreError.Unavailable), store.saveNew("never moved"))

            assertEquals(TEXT, store.load()?.json)
            assertEquals("the write must have happened, so this was the move failing", "never moved", tempFile.readText())
        }

    @Test
    fun theInjectedStoreKeepsTheCopyInTheAppsPrivateFilesDirectory() =
        runBlocking<Unit> {
            val injected = FilePreRestoreSnapshotStore(context, Dispatchers.IO)
            try {
                injected.saveNew(TEXT)

                assertEquals(TEXT, File(context.filesDir, newFile.name).readText())
            } finally {
                injected.clear()
            }
        }

    private suspend fun saveAndPromote(text: String) {
        assertEquals(DomainResult.Success(Unit), store.saveNew(text))
        store.promoteNew()
    }

    private fun putNonEmptyDirectoryAt(file: File) {
        file.deleteRecursively()
        assertTrue(file.mkdirs())
        File(file, "keeps-the-directory-non-empty").writeText("x")
    }

    private companion object {
        const val TEXT = """{"schemaVersion":2,"note":"Agachamento é ótimo 💪"}"""
        const val OLD_MILLIS = 1_700_000_000_000L
        const val LEFTOVER_MILLIS = 1_750_000_000_000L
        const val FILE_TIME_SLACK_SECONDS = 2L
    }
}
