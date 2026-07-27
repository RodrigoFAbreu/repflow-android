package com.repflow.app.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RestTimerTest {
    private val now: Instant = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun `start sets endAt duration seconds ahead of now`() {
        val timer = RestTimer.start(90, now)

        assertEquals(now.plusSeconds(90), timer.endAt)
        assertEquals(90, timer.totalDurationSeconds)
    }

    @Test
    fun `remainingSeconds is the gap to endAt`() {
        val timer = RestTimer.start(90, now)

        assertEquals(60L, timer.remainingSeconds(now.plusSeconds(30)))
    }

    @Test
    fun `remainingSeconds never goes negative past expiry`() {
        val timer = RestTimer.start(10, now)

        assertEquals(0L, timer.remainingSeconds(now.plusSeconds(100)))
    }

    @Test
    fun `isExpired is true once now reaches endAt`() {
        val timer = RestTimer.start(10, now)

        assertFalse(timer.isExpired(now.plusSeconds(9)))
        assertTrue(timer.isExpired(now.plusSeconds(10)))
        assertTrue(timer.isExpired(now.plusSeconds(11)))
    }

    @Test
    fun `withAddedSeconds pushes endAt further out`() {
        val timer = RestTimer.start(90, now)

        val adjusted = timer.withAddedSeconds(15)

        assertEquals(now.plusSeconds(105), adjusted.endAt)
        assertEquals(90, adjusted.totalDurationSeconds)
    }

    @Test
    fun `withRemovedSeconds pulls endAt closer`() {
        val timer = RestTimer.start(90, now)

        val adjusted = timer.withRemovedSeconds(15, now)

        assertEquals(now.plusSeconds(75), adjusted.endAt)
    }

    @Test
    fun `withRemovedSeconds clamps to now instead of going negative`() {
        val timer = RestTimer.start(10, now)

        val adjusted = timer.withRemovedSeconds(100, now)

        assertEquals(now, adjusted.endAt)
    }
}
