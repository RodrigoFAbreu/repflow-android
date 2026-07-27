package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RecordFutsalSessionTest {
    private val clock = FixedClock(Instant.parse("2026-01-01T00:00:00Z"))
    private val identifierGenerator = SequentialIdentifierGenerator("futsal")
    private val repository = InMemoryFutsalRepository()
    private val useCase = RecordFutsalSession(repository, clock, identifierGenerator)

    @Test
    fun `invoke creates a new session and the load is derived`() =
        runTest {
            val result =
                useCase(RecordFutsalSessionCommand(date = LocalDate.parse("2026-01-01"), durationMinutes = 60, sessionRpe = 6.0))

            val id = (result as DomainResult.Success).value
            val stored = repository.findForDate(LocalDate.parse("2026-01-01"))
            assertEquals(id, stored?.id)
            assertEquals(360.0, stored?.load ?: 0.0, 0.0)
        }

    @Test
    fun `invoke replaces the existing session for the same date`() =
        runTest {
            val date = LocalDate.parse("2026-01-01")
            useCase(RecordFutsalSessionCommand(date = date, durationMinutes = 60, sessionRpe = 5.0))
            val firstId = repository.findForDate(date)?.id

            val result = useCase(RecordFutsalSessionCommand(date = date, durationMinutes = 90, sessionRpe = 8.0))

            val updated = repository.findForDate(date)
            assertEquals(firstId, (result as DomainResult.Success).value)
            assertEquals(720.0, updated?.load ?: 0.0, 0.0)
        }

    @Test
    fun `invoke surfaces a validation failure without writing to the repository`() =
        runTest {
            val result = useCase(RecordFutsalSessionCommand(date = LocalDate.parse("2026-01-01"), durationMinutes = 0, sessionRpe = 5.0))

            assertTrue(result is DomainResult.Failure)
            assertEquals(null, repository.findForDate(LocalDate.parse("2026-01-01")))
        }
}
