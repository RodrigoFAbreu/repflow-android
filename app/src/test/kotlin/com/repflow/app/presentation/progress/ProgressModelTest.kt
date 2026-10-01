package com.repflow.app.presentation.progress

import com.repflow.app.application.progress.ProgressPoint
import com.repflow.app.domain.workout.WorkoutSessionId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class ProgressModelTest {
    @Test
    fun `bar heights follow the prototype - lowest at 14 percent, highest at 96`() {
        val heights = barHeightFractions(listOf(BigDecimal("72.5"), BigDecimal("77.5"), BigDecimal("82.5")))

        assertEquals(listOf(0.14f, 0.55f, 0.96f), heights)
    }

    @Test
    fun `a flat series draws every bar at the floor, and nothing draws nothing`() {
        assertEquals(listOf(0.14f, 0.14f), barHeightFractions(listOf(BigDecimal("80"), BigDecimal("80.0"))))
        assertEquals(emptyList<Float>(), barHeightFractions(emptyList()))
    }

    @Test
    fun `a month labels the first bar and each bar that starts a new month`() {
        val points =
            listOf("2026-05-05", "2026-05-12", "2026-06-02", "2026-06-09", "2026-07-30", "2027-07-01").mapIndexed { index, date ->
                ProgressPoint(WorkoutSessionId("s$index"), Instant.parse("${date}T08:00:00Z"), BigDecimal.ONE)
            }

        assertEquals(
            listOf(YearMonth.of(2026, 5), null, YearMonth.of(2026, 6), null, YearMonth.of(2026, 7), YearMonth.of(2027, 7)),
            barMonthLabels(points, ZoneOffset.UTC),
        )
    }

    @Test
    fun `a delta always carries its sign`() {
        assertEquals("+10", signedNumber(BigDecimal("10.0")))
        assertEquals("+0", signedNumber(BigDecimal.ZERO))
        assertEquals("−2.5", signedNumber(BigDecimal("-2.50")))
        assertEquals("2310", plainNumber(BigDecimal("2310")))
    }
}
