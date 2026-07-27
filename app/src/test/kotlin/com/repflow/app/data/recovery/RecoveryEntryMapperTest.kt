package com.repflow.app.data.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecoveryEntryMapperTest {
    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val entry =
            requireSuccess(
                RecoveryEntry.create(
                    id = RecoveryEntryId("recovery-1"),
                    date = LocalDate.parse("2026-01-01"),
                    sleepQuality = 3,
                    energy = 2,
                    legDoms = 1,
                    heelStiffness = 0,
                    painWhileWalking = 0,
                    heavyLegs = 2,
                    futsalInPrevious24h = true,
                    futsalExpectedNext24h = false,
                    notes = "Slept ok",
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )

        val roundTripped = requireSuccess(RecoveryEntryMapper.toDomain(RecoveryEntryMapper.toEntity(entry)))

        assertEquals(entry, roundTripped)
    }

    private fun requireSuccess(result: DomainResult<RecoveryEntry, *>): RecoveryEntry =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }
}
