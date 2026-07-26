package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecordRecoveryEntryTest {
    private val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val identifierGenerator = SequentialIdentifierGenerator()
    private val repository = InMemoryRecoveryRepository()
    private val useCase = RecordRecoveryEntry(repository, clock, identifierGenerator)

    @Test
    fun `invoke creates a new entry when none exists for the date`() =
        runTest {
            val result =
                useCase(
                    RecordRecoveryEntryCommand(
                        date = LocalDate.parse("2026-01-01"),
                        sleepQuality = 3,
                        energy = 3,
                        legDoms = 1,
                        heelStiffness = 0,
                        painWhileWalking = 0,
                        heavyLegs = 1,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                    ),
                )

            val id = (result as DomainResult.Success).value
            val stored = repository.findForDate(LocalDate.parse("2026-01-01"))
            assertEquals(id, stored?.id)
            assertEquals(3, stored?.sleepQuality)
        }

    @Test
    fun `invoke replaces the existing entry for the same date rather than duplicating`() =
        runTest {
            val date = LocalDate.parse("2026-01-01")
            useCase(
                RecordRecoveryEntryCommand(
                    date = date,
                    sleepQuality = 2,
                    energy = 2,
                    legDoms = 2,
                    heelStiffness = 2,
                    painWhileWalking = 2,
                    heavyLegs = 2,
                    futsalInPrevious24h = false,
                    futsalExpectedNext24h = false,
                    notes = null,
                ),
            )
            val firstId = repository.findForDate(date)?.id

            val result =
                useCase(
                    RecordRecoveryEntryCommand(
                        date = date,
                        sleepQuality = 4,
                        energy = 4,
                        legDoms = 0,
                        heelStiffness = 0,
                        painWhileWalking = 0,
                        heavyLegs = 0,
                        futsalInPrevious24h = true,
                        futsalExpectedNext24h = true,
                        notes = "Better",
                    ),
                )

            val updated = repository.findForDate(date)
            assertEquals(firstId, (result as DomainResult.Success).value)
            assertEquals(4, updated?.sleepQuality)
            assertTrue(updated?.futsalInPrevious24h == true)
        }

    @Test
    fun `invoke surfaces a validation failure without writing to the repository`() =
        runTest {
            val result =
                useCase(
                    RecordRecoveryEntryCommand(
                        date = LocalDate.parse("2026-01-01"),
                        sleepQuality = 9,
                        energy = 2,
                        legDoms = 2,
                        heelStiffness = 2,
                        painWhileWalking = 2,
                        heavyLegs = 2,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                    ),
                )

            assertTrue(result is DomainResult.Failure)
            assertEquals(null, repository.findForDate(LocalDate.parse("2026-01-01")))
        }
}
