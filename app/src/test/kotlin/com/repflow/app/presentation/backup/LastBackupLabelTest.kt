package com.repflow.app.presentation.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class LastBackupLabelTest {
    private val zone = ZoneId.of("Europe/Lisbon")

    private fun label(
        lastBackupAt: String,
        now: String,
    ) = lastBackupLabel(Instant.parse(lastBackupAt), Instant.parse(now), zone)

    @Test
    fun `a backup earlier the same day is today`() {
        assertEquals(LastBackupLabel.Today, label("2026-10-03T08:00:00Z", "2026-10-03T20:00:00Z"))
    }

    @Test
    fun `the previous calendar day is yesterday even when under 24 hours ago`() {
        // 23:50 local the day before, ten minutes after local midnight.
        assertEquals(LastBackupLabel.Yesterday, label("2026-10-02T22:50:00Z", "2026-10-02T23:10:00Z"))
    }

    @Test
    fun `two or more days is counted in calendar days`() {
        assertEquals(LastBackupLabel.DaysAgo(2), label("2026-10-01T21:04:00Z", "2026-10-03T06:00:00Z"))
        assertEquals(LastBackupLabel.DaysAgo(40), label("2026-08-24T12:00:00Z", "2026-10-03T12:00:00Z"))
    }

    @Test
    fun `a timestamp in the future reads as today`() {
        assertEquals(LastBackupLabel.Today, label("2026-10-04T12:00:00Z", "2026-10-03T12:00:00Z"))
    }
}
