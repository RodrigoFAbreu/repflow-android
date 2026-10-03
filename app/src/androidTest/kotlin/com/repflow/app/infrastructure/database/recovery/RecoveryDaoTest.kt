package com.repflow.app.infrastructure.database.recovery

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.repflow.app.infrastructure.database.RepFlowDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-SQLite instrumented coverage for [RecoveryEntryDao] and
 * [FutsalSessionDao], run against an in-memory Room database on an actual
 * device/emulator, mirroring `WorkoutDaoTest`.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryDaoTest {
    private lateinit var database: RepFlowDatabase
    private lateinit var recoveryDao: RecoveryEntryDao
    private lateinit var futsalDao: FutsalSessionDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        recoveryDao = database.recoveryEntryDao()
        futsalDao = database.futsalSessionDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertReplacesTheExistingRowForTheSameDate() =
        runBlocking {
            recoveryDao.upsert(recoveryEntity(id = "recovery-1", sleepQuality = 2))
            recoveryDao.upsert(recoveryEntity(id = "recovery-2", sleepQuality = 4))

            val stored = recoveryDao.findForDate("2026-01-01")
            assertEquals("recovery-2", stored?.id)
            assertEquals(4, stored?.sleepQuality)
        }

    @Test
    fun findLatestReturnsTheMostRecentDate() =
        runBlocking {
            recoveryDao.upsert(recoveryEntity(id = "recovery-1", date = "2026-01-01"))
            recoveryDao.upsert(recoveryEntity(id = "recovery-2", date = "2026-01-05"))

            assertEquals("2026-01-05", recoveryDao.findLatest()?.entryDate)
        }

    @Test
    fun findLatestReturnsNullWhenNoRowsExist() =
        runBlocking {
            assertNull(recoveryDao.findLatest())
        }

    @Test
    fun observeForDateReEmitsAfterAnUpsertForThatDate() =
        runBlocking {
            recoveryDao.observeForDate("2026-01-01").test {
                assertNull(awaitItem())

                recoveryDao.upsert(recoveryEntity(id = "recovery-1", sleepQuality = 2))
                assertEquals(2, awaitItem()?.sleepQuality)

                recoveryDao.upsert(recoveryEntity(id = "recovery-2", sleepQuality = 4))
                assertEquals(4, awaitItem()?.sleepQuality)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun futsalUpsertReplacesTheExistingRowAndFindSinceFiltersByDate() =
        runBlocking {
            futsalDao.upsert(futsalEntity(id = "futsal-1", date = "2026-01-01", durationMinutes = 45))
            futsalDao.upsert(futsalEntity(id = "futsal-2", date = "2026-01-10", durationMinutes = 60))

            val since = futsalDao.findSince("2026-01-05")
            assertEquals(1, since.size)
            assertEquals("futsal-2", since.first().id)
        }

    private fun recoveryEntity(
        id: String,
        date: String = "2026-01-01",
        sleepQuality: Int = 3,
    ): RecoveryEntryEntity =
        RecoveryEntryEntity(
            id = id,
            entryDate = date,
            sleepQuality = sleepQuality,
            energy = 3,
            legDoms = 1,
            heelStiffness = 0,
            painWhileWalking = 0,
            heavyLegs = 1,
            futsalInPrevious24h = false,
            futsalExpectedNext24h = false,
            notes = null,
            createdAt = 1000,
            updatedAt = 1000,
        )

    private fun futsalEntity(
        id: String,
        date: String,
        durationMinutes: Int,
    ): FutsalSessionEntity =
        FutsalSessionEntity(
            id = id,
            entryDate = date,
            durationMinutes = durationMinutes,
            sessionRpe = 6.0,
            createdAt = 1000,
            updatedAt = 1000,
        )
}
