package com.repflow.app.domain.recovery

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecoveryEntryTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val id = RecoveryEntryId("11111111-1111-1111-1111-111111111111")
    private val date: LocalDate = LocalDate.parse("2026-01-01")

    @Test
    fun `create returns a recovery entry with matching timestamps`() {
        val entry =
            requireSuccess(
                RecoveryEntry.create(
                    id = id,
                    date = date,
                    sleepQuality = 3,
                    energy = 2,
                    legDoms = 1,
                    heelStiffness = 0,
                    painWhileWalking = 0,
                    heavyLegs = 2,
                    futsalInPrevious24h = true,
                    futsalExpectedNext24h = false,
                    notes = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )

        assertEquals(createdAt, entry.createdAt)
        assertEquals(date, entry.date)
        assertTrue(entry.futsalInPrevious24h)
    }

    @Test
    fun `create rejects a scale value outside 0 to 5`() {
        val result =
            RecoveryEntry.create(
                id = id,
                date = date,
                sleepQuality = 6,
                energy = 2,
                legDoms = 1,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 2,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        assertEquals(
            DomainResult.Failure(RecoveryValidationError.ScaleValueOutOfRange),
            result,
        )
    }

    @Test
    fun `create rejects notes longer than the maximum length`() {
        val result =
            RecoveryEntry.create(
                id = id,
                date = date,
                sleepQuality = 3,
                energy = 2,
                legDoms = 1,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 2,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = "x".repeat(RecoveryEntry.NOTES_MAX_LENGTH + 1),
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        assertEquals(
            DomainResult.Failure(RecoveryValidationError.NotesTooLong),
            result,
        )
    }

    @Test
    fun `create rejects updatedAt before createdAt`() {
        val result =
            RecoveryEntry.create(
                id = id,
                date = date,
                sleepQuality = 3,
                energy = 2,
                legDoms = 1,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 2,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = createdAt,
                updatedAt = createdAt.minusSeconds(1),
            )

        assertEquals(
            DomainResult.Failure(RecoveryValidationError.UpdatedBeforeCreated),
            result,
        )
    }

    @Test
    fun `update preserves createdAt and applies new fields`() {
        val entry =
            requireSuccess(
                RecoveryEntry.create(
                    id = id,
                    date = date,
                    sleepQuality = 3,
                    energy = 2,
                    legDoms = 1,
                    heelStiffness = 0,
                    painWhileWalking = 0,
                    heavyLegs = 2,
                    futsalInPrevious24h = false,
                    futsalExpectedNext24h = false,
                    notes = null,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )
        val updatedAt = createdAt.plusSeconds(60)

        val updated =
            requireSuccess(
                entry.update(
                    sleepQuality = 4,
                    energy = 4,
                    legDoms = 0,
                    heelStiffness = 0,
                    painWhileWalking = 0,
                    heavyLegs = 0,
                    futsalInPrevious24h = false,
                    futsalExpectedNext24h = true,
                    notes = "Felt great",
                    at = updatedAt,
                ),
            )

        assertEquals(createdAt, updated.createdAt)
        assertEquals(updatedAt, updated.updatedAt)
        assertEquals(4, updated.sleepQuality)
        assertTrue(updated.futsalExpectedNext24h)
        assertEquals("Felt great", updated.notes)
    }

    private fun requireSuccess(result: DomainResult<RecoveryEntry, RecoveryValidationError>): RecoveryEntry =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }
}
