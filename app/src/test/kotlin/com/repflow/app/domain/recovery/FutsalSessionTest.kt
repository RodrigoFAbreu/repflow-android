package com.repflow.app.domain.recovery

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FutsalSessionTest {
    private val createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val id = FutsalSessionId("22222222-2222-2222-2222-222222222222")
    private val date: LocalDate = LocalDate.parse("2026-01-01")

    @Test
    fun `create computes load as duration times session rpe`() {
        val session =
            requireSuccess(
                FutsalSession.create(
                    id = id,
                    date = date,
                    durationMinutes = 60,
                    sessionRpe = 7.0,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )

        assertEquals(420.0, session.load, 0.0)
    }

    @Test
    fun `create rejects a non-positive duration`() {
        val result =
            FutsalSession.create(
                id = id,
                date = date,
                durationMinutes = 0,
                sessionRpe = 5.0,
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        assertEquals(DomainResult.Failure(FutsalValidationError.DurationNotPositive), result)
    }

    @Test
    fun `create rejects a session rpe outside 0 to 10`() {
        val result =
            FutsalSession.create(
                id = id,
                date = date,
                durationMinutes = 60,
                sessionRpe = 10.5,
                createdAt = createdAt,
                updatedAt = createdAt,
            )

        assertEquals(DomainResult.Failure(FutsalValidationError.SessionRpeOutOfRange), result)
    }

    @Test
    fun `create rejects updatedAt before createdAt`() {
        val result =
            FutsalSession.create(
                id = id,
                date = date,
                durationMinutes = 60,
                sessionRpe = 5.0,
                createdAt = createdAt,
                updatedAt = createdAt.minusSeconds(1),
            )

        assertEquals(DomainResult.Failure(FutsalValidationError.UpdatedBeforeCreated), result)
    }

    @Test
    fun `update preserves createdAt and recomputes load`() {
        val session =
            requireSuccess(
                FutsalSession.create(
                    id = id,
                    date = date,
                    durationMinutes = 60,
                    sessionRpe = 5.0,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                ),
            )
        val updatedAt = createdAt.plusSeconds(60)

        val updated =
            requireSuccess(session.update(durationMinutes = 90, sessionRpe = 8.0, at = updatedAt))

        assertEquals(createdAt, updated.createdAt)
        assertEquals(updatedAt, updated.updatedAt)
        assertEquals(720.0, updated.load, 0.0)
    }

    private fun requireSuccess(result: DomainResult<FutsalSession, FutsalValidationError>): FutsalSession =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }
}
