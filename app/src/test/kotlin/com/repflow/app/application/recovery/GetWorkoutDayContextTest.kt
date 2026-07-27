package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class GetWorkoutDayContextTest {
    private val now = Instant.parse("2026-01-10T12:00:00Z")
    private val clock = FixedClock(now)
    private val recoveryRepository = InMemoryRecoveryRepository()
    private val futsalRepository = InMemoryFutsalRepository()
    private val useCase = GetWorkoutDayContext(recoveryRepository, futsalRepository, clock)

    @Test
    fun `invoke returns nulls when no data has been recorded`() =
        runTest {
            val context = useCase()

            assertNull(context.latestRecoveryEntry)
            assertNull(context.recentFutsalSession)
        }

    @Test
    fun `invoke returns the latest recovery entry regardless of age`() =
        runTest {
            recoveryRepository.seed(recoveryEntry(LocalDate.parse("2026-01-01")))
            recoveryRepository.seed(recoveryEntry(LocalDate.parse("2026-01-05")))

            val context = useCase()

            assertEquals(LocalDate.parse("2026-01-05"), context.latestRecoveryEntry?.date)
        }

    @Test
    fun `invoke excludes a futsal session recorded more than 24 hours ago`() =
        runTest {
            futsalRepository.seed(futsalSession(LocalDate.parse("2026-01-05")))

            val context = useCase()

            assertNull(context.recentFutsalSession)
        }

    @Test
    fun `invoke includes a futsal session recorded within the last 24 hours`() =
        runTest {
            futsalRepository.seed(futsalSession(LocalDate.parse("2026-01-10")))

            val context = useCase()

            assertEquals(LocalDate.parse("2026-01-10"), context.recentFutsalSession?.date)
        }

    private fun recoveryEntry(date: LocalDate): RecoveryEntry =
        requireSuccess(
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-$date"),
                date = date,
                sleepQuality = 3,
                energy = 3,
                legDoms = 1,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 1,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

    private fun futsalSession(date: LocalDate): FutsalSession =
        requireSuccessFutsal(
            FutsalSession.create(
                id = FutsalSessionId("futsal-$date"),
                date = date,
                durationMinutes = 60,
                sessionRpe = 6.0,
                createdAt = now,
                updatedAt = now,
            ),
        )

    private fun requireSuccess(result: DomainResult<RecoveryEntry, *>): RecoveryEntry =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }

    private fun requireSuccessFutsal(result: DomainResult<FutsalSession, *>): FutsalSession =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> error("Expected success but was ${result.error}")
        }
}
