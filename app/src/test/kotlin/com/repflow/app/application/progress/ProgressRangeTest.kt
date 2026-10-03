package com.repflow.app.application.progress

import com.repflow.app.domain.workout.WorkoutSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Remediation-1-remediation-1 CP2 (Q7, review O-13): date ranges with a month-end clamp and an exclusive bound. */
class ProgressRangeTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun bound(
        range: ProgressRange,
        now: String,
        zone: ZoneId = utc,
    ): Instant? = range.startsAfter(Instant.parse(now), zone)

    @Test
    fun `all has no bound`() {
        assertNull(bound(ProgressRange.ALL, "2026-05-31T12:00:00Z"))
    }

    @Test
    fun `three and six months reach back by calendar months`() {
        assertEquals(Instant.parse("2026-02-15T09:30:00Z"), bound(ProgressRange.THREE_MONTHS, "2026-05-15T09:30:00Z"))
        assertEquals(Instant.parse("2025-11-15T09:30:00Z"), bound(ProgressRange.SIX_MONTHS, "2026-05-15T09:30:00Z"))
    }

    @Test
    fun `a range crossing a year boundary lands in the previous year`() {
        assertEquals(Instant.parse("2025-10-15T00:00:00Z"), bound(ProgressRange.THREE_MONTHS, "2026-01-15T00:00:00Z"))
        assertEquals(Instant.parse("2025-07-15T00:00:00Z"), bound(ProgressRange.SIX_MONTHS, "2026-01-15T00:00:00Z"))
    }

    @Test
    fun `a month end clamps to the last day of a shorter target month`() {
        assertEquals(Instant.parse("2026-02-28T12:00:00Z"), bound(ProgressRange.THREE_MONTHS, "2026-05-31T12:00:00Z"))
        assertEquals(Instant.parse("2024-02-29T12:00:00Z"), bound(ProgressRange.THREE_MONTHS, "2024-05-31T12:00:00Z"))
        assertEquals(Instant.parse("2026-02-28T12:00:00Z"), bound(ProgressRange.SIX_MONTHS, "2026-08-31T12:00:00Z"))
    }

    @Test
    fun `the bound is the same local time in the zone`() {
        // 12:00 in Lisbon on 31 May is 11:00Z (WEST); 28 February is WET, 12:00Z.
        assertEquals(
            Instant.parse("2026-02-28T12:00:00Z"),
            bound(ProgressRange.THREE_MONTHS, "2026-05-31T11:00:00Z", ZoneId.of("Europe/Lisbon")),
        )
    }

    @Test
    fun `a session exactly at the bound is outside and one just after is inside`() {
        val now = Instant.parse("2026-05-15T09:30:00Z")
        val edge = Instant.parse("2026-02-15T09:30:00Z")
        val series =
            ProgressSeries(
                listOf(
                    ProgressPoint(WorkoutSessionId("before"), edge.minusSeconds(1), BigDecimal(1)),
                    ProgressPoint(WorkoutSessionId("at"), edge, BigDecimal(2)),
                    ProgressPoint(WorkoutSessionId("after"), edge.plusSeconds(1), BigDecimal(3)),
                    ProgressPoint(WorkoutSessionId("now"), now, BigDecimal(4)),
                ),
            )

        assertEquals(
            listOf("after", "now"),
            series.within(ProgressRange.THREE_MONTHS, now, utc).points.map { it.sessionId.value },
        )
        assertEquals(4, series.within(ProgressRange.ALL, now, utc).points.size)
    }

    @Test
    fun `the delta and its percent read the points inside the range`() {
        val now = Instant.parse("2026-05-15T00:00:00Z")
        val series =
            ProgressSeries(
                listOf(
                    ProgressPoint(WorkoutSessionId("old"), Instant.parse("2025-12-01T00:00:00Z"), BigDecimal(50)),
                    ProgressPoint(WorkoutSessionId("a"), Instant.parse("2026-03-01T00:00:00Z"), BigDecimal(70)),
                    ProgressPoint(WorkoutSessionId("b"), Instant.parse("2026-05-01T00:00:00Z"), BigDecimal(80)),
                ),
            )

        val three = series.within(ProgressRange.THREE_MONTHS, now, utc)
        assertEquals(0, BigDecimal(10).compareTo(three.delta))
        assertEquals(0, BigDecimal(14).compareTo(three.deltaPercent))
        val all = series.within(ProgressRange.ALL, now, utc)
        assertEquals(0, BigDecimal(30).compareTo(all.delta))
        assertEquals(0, BigDecimal(60).compareTo(all.deltaPercent))
    }

    @Test
    fun `a zero start has no percent and one point has no delta`() {
        val zero =
            ProgressSeries(
                listOf(
                    ProgressPoint(WorkoutSessionId("a"), Instant.parse("2026-05-01T00:00:00Z"), BigDecimal.ZERO),
                    ProgressPoint(WorkoutSessionId("b"), Instant.parse("2026-05-08T00:00:00Z"), BigDecimal(5)),
                ),
            )
        assertEquals(0, BigDecimal(5).compareTo(zero.delta))
        assertNull(zero.deltaPercent)
        val single = ProgressSeries(zero.points.take(1))
        assertNull(single.delta)
        assertNull(single.deltaPercent)
    }
}
