package com.repflow.app.data.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FutsalSessionMapperTest {
    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val createdAt = Instant.parse("2026-01-01T00:00:00Z")
        val session =
            requireSuccess(
                FutsalSession.create(
                    id = FutsalSessionId("futsal-1"),
                    date = LocalDate.parse("2026-01-01"),
                    durationMinutes = 60,
                    sessionRpe = 7.0,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )

        val roundTripped = requireSuccess(FutsalSessionMapper.toDomain(FutsalSessionMapper.toEntity(session)))

        assertEquals(session, roundTripped)
    }

    private fun requireSuccess(result: DomainResult<FutsalSession, *>): FutsalSession =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }
}
