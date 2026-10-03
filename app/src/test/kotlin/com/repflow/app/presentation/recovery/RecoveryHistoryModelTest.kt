package com.repflow.app.presentation.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** `3d`'s fourteen-day trend (remediation-1 CP13 items 3 and 4), on the JVM. */
class RecoveryHistoryModelTest {
    private val today = LocalDate.of(2026, 8, 11)
    private val at = Instant.parse("2026-08-01T00:00:00Z")

    private fun entry(
        date: LocalDate,
        sleep: Int,
        energy: Int,
    ): RecoveryEntry =
        (
            RecoveryEntry.create(
                id = RecoveryEntryId("r-$date"),
                date = date,
                sleepQuality = sleep,
                energy = energy,
                legDoms = 0,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 0,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = at,
                updatedAt = at,
            ) as DomainResult.Success
        ).value

    private fun futsal(date: LocalDate): FutsalSession =
        (
            FutsalSession.create(
                id = FutsalSessionId("f-$date"),
                date = date,
                durationMinutes = 50,
                sessionRpe = 8.0,
                createdAt = at,
                updatedAt = at,
            ) as DomainResult.Success
        ).value

    @Test
    fun `the window is the fourteen days ending today, oldest first`() {
        val trend = recoveryTrendOf(emptyList(), emptyList(), today)

        assertEquals(14, trend.days.size)
        assertEquals(LocalDate.of(2026, 7, 29), trend.days.first().date)
        assertEquals(today, trend.days.last().date)
    }

    @Test
    fun `a day carries its check-in, and a day without one carries none`() {
        val trend = recoveryTrendOf(listOf(entry(today.minusDays(1), sleep = 5, energy = 4)), emptyList(), today)

        val yesterday = trend.days[12]
        assertEquals(5, yesterday.sleepQuality)
        assertEquals(4, yesterday.energy)
        assertTrue(yesterday.hasCheckIn)
        assertFalse(trend.days.last().hasCheckIn)
        assertNull(trend.days.last().sleepQuality)
    }

    @Test
    fun `entries outside the window are left out of the chart and its averages`() {
        val trend =
            recoveryTrendOf(
                listOf(entry(today.minusDays(14), sleep = 0, energy = 0), entry(today, sleep = 4, energy = 3)),
                emptyList(),
                today,
            )

        assertEquals(1, trend.days.count { it.hasCheckIn })
        assertEquals(BigDecimal("4.0"), trend.averageSleep)
        assertEquals(BigDecimal("3.0"), trend.averageEnergy)
    }

    @Test
    fun `averages are over the days with a check-in, to one decimal`() {
        val trend =
            recoveryTrendOf(
                listOf(
                    entry(today, sleep = 4, energy = 3),
                    entry(today.minusDays(2), sleep = 3, energy = 2),
                    entry(today.minusDays(5), sleep = 4, energy = 4),
                ),
                emptyList(),
                today,
            )

        assertEquals(BigDecimal("3.7"), trend.averageSleep)
        assertEquals(BigDecimal("3.0"), trend.averageEnergy)
    }

    @Test
    fun `a futsal session marks its own day`() {
        val trend = recoveryTrendOf(emptyList(), listOf(futsal(today.minusDays(3))), today)

        assertEquals(listOf(today.minusDays(3)), trend.days.filter { it.hadFutsal }.map { it.date })
        assertFalse(trend.isEmpty)
        assertNull(trend.averageSleep)
    }

    @Test
    fun `the readout opens on the most recent day with a check-in`() {
        val trend = recoveryTrendOf(listOf(entry(today.minusDays(4), sleep = 2, energy = 2)), emptyList(), today)

        assertEquals(9, trend.defaultSelectedIndex)
    }

    @Test
    fun `with nothing in the window the trend is empty and opens on today`() {
        val trend = recoveryTrendOf(listOf(entry(today.minusDays(30), sleep = 2, energy = 2)), emptyList(), today)

        assertTrue(trend.isEmpty)
        assertEquals(13, trend.defaultSelectedIndex)
    }
}
