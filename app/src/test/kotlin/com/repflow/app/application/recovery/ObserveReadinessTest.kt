package com.repflow.app.application.recovery

import app.cash.turbine.test
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.ReadinessBand
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ObserveReadinessTest {
    private val repository = InMemoryRecoveryRepository()
    private val observeReadiness = ObserveReadiness(repository)

    private val today: LocalDate = LocalDate.parse("2026-08-11")

    @Test
    fun `no entry for the date emits none`() =
        runTest {
            observeReadiness(today).test {
                assertNull(awaitItem())
            }
        }

    @Test
    fun `an entry dated yesterday only is never carried forward`() =
        runTest {
            repository.seed(seedEntry(today.minusDays(1)))

            observeReadiness(today).test {
                assertNull(awaitItem())
            }
        }

    @Test
    fun `upserting today's entry emits a score on the same subscription`() =
        runTest {
            observeReadiness(today).test {
                assertNull(awaitItem())

                repository.upsert(seedEntry(today))

                val readiness = awaitItem()
                assertEquals(75, readiness?.score)
                assertEquals(ReadinessBand.READY, readiness?.band)

                // A re-recorded check-in for the same day re-scores without resubscribing.
                repository.upsert(seedEntry(today, heavyLegs = 3))

                assertEquals(73, awaitItem()?.score)
            }
        }

    @Test
    fun `a write for another date does not re-emit an unchanged score`() =
        runTest {
            repository.seed(seedEntry(today))

            observeReadiness(today).test {
                assertEquals(75, awaitItem()?.score)

                repository.upsert(seedEntry(today.minusDays(2)))

                expectNoEvents()
            }
        }

    /** The prototype's own seed check-in: sleep 4, energy 3, DOMS 2, heel 1, pain 0, heavy legs 2 - 75, Ready. */
    private fun seedEntry(
        date: LocalDate,
        heavyLegs: Int = 2,
    ): RecoveryEntry {
        val at = Instant.parse("2026-08-11T07:00:00Z")
        val result =
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-$date"),
                date = date,
                sleepQuality = 4,
                energy = 3,
                legDoms = 2,
                heelStiffness = 1,
                painWhileWalking = 0,
                heavyLegs = heavyLegs,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = at,
                updatedAt = at,
            )
        return (result as DomainResult.Success).value
    }
}
