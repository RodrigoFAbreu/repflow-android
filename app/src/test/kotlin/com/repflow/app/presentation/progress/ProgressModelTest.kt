package com.repflow.app.presentation.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ProgressModelTest {
    private fun values(vararg v: String) = v.map(::BigDecimal)

    @Test
    fun `the scale puts the lowest value on the bottom line and the highest on the top`() {
        val scale = requireNotNull(ChartScale.of(values("72.5", "77.5", "82.5")))

        assertEquals(listOf(BigDecimal("82.5"), BigDecimal("77.500000"), BigDecimal("72.5")), scale.gridValues)
        assertEquals(listOf(1f, 0.5f, 0f), scale.gridFractions())
        assertEquals(0.5f, scale.fractionOf(BigDecimal("77.5")))
        assertFalse(scale.isFlat)
    }

    @Test
    fun `a flat series sits on the middle line with one label, and no values draw nothing`() {
        val flat = requireNotNull(ChartScale.of(values("80", "80.0")))

        assertTrue(flat.isFlat)
        assertEquals(1, flat.gridValues.size)
        assertEquals(0.5f, flat.fractionOf(BigDecimal("80")))
        assertEquals(null, ChartScale.of(emptyList()))
        assertEquals(0.5f, requireNotNull(ChartScale.of(values("100"))).fractionOf(BigDecimal("100")))
    }

    @Test
    fun `few points show every label`() {
        // 5 points over 300px, 40px labels with an 8px gap: 75px apart, every label fits.
        assertEquals(listOf(0, 1, 2, 3, 4), xLabelIndices(5, 300f, 40f, 8f))
        assertEquals(listOf(0), xLabelIndices(1, 300f, 40f, 8f))
        assertEquals(emptyList<Int>(), xLabelIndices(0, 300f, 40f, 8f))
    }

    @Test
    fun `the first and last labels are always drawn and the label nearest the last is the one dropped`() {
        // 11 points over 100px = 10px apart; a 25px label plus 8px gap needs a stride of 4: 0, 4, 8 - and 8 is
        // only 2 points (20px) from the last, so it gives way to point 10.
        assertEquals(listOf(0, 4, 10), xLabelIndices(11, 100f, 25f, 8f))
        // A stride that lands on the last point keeps it once.
        assertEquals(listOf(0, 4, 8), xLabelIndices(9, 80f, 25f, 8f))
    }

    @Test
    fun `consecutive labels are at least a label width plus the gap apart, for dense series at both phone widths`() {
        val labelWidthPx = 36f * 2.625f
        val gapPx = 8f * 2.625f
        for (widthDp in listOf(360f, 384f)) {
            // The card's plot: the screen less 16 gutters, 14 card padding, the 34 + 6 axis and a label's half-width either side.
            val plotPx = (widthDp - 32f - 28f - 40f) * 2.625f - labelWidthPx
            for (count in listOf(2, 3, 7, 60, 120, 400)) {
                val shown = xLabelIndices(count, plotPx, labelWidthPx, gapPx)
                val spacing = plotPx / (count - 1)
                assertEquals(0, shown.first())
                assertEquals(count - 1, shown.last())
                shown.zipWithNext().forEach { (a, b) ->
                    assertTrue(
                        "$count points at $widthDp dp: labels $a and $b are ${(b - a) * spacing}px apart",
                        (b - a) * spacing >= labelWidthPx + gapPx - 0.001f,
                    )
                }
            }
        }
    }

    @Test
    fun `a touch lands on the nearest point, however many there are`() {
        assertEquals(0, nearestPointIndex(x = 10f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 4))
        assertEquals(0, nearestPointIndex(x = 149f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 4))
        assertEquals(1, nearestPointIndex(x = 151f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 4))
        assertEquals(3, nearestPointIndex(x = 999f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 4))
        assertEquals(0, nearestPointIndex(x = 250f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 1))
        assertEquals(199, nearestPointIndex(x = 400f, plotLeftPx = 100f, plotWidthPx = 300f, pointCount = 200))
    }

    @Test
    fun `points are spread across the plot and a lone point sits in the middle`() {
        assertEquals(100f, pointX(0, 3, 100f, 200f))
        assertEquals(200f, pointX(1, 3, 100f, 200f))
        assertEquals(300f, pointX(2, 3, 100f, 200f))
        assertEquals(200f, pointX(0, 1, 100f, 200f))
    }

    @Test
    fun `a date drops the year only in the latest point's year`() {
        val at = Instant.parse("2025-08-12T12:00:00Z")

        assertEquals("12 Aug", dateLabel(at, ZoneOffset.UTC, Locale.UK, withYear = false))
        assertEquals("12 Aug 2025", dateLabel(at, ZoneOffset.UTC, Locale.UK, withYear = true))
    }

    @Test
    fun `a delta always carries its sign`() {
        assertEquals("+10", signedNumber(BigDecimal("10.0"), Locale.US))
        assertEquals("+0", signedNumber(BigDecimal.ZERO, Locale.US))
        assertEquals("−2.5", signedNumber(BigDecimal("-2.50"), Locale.US))
        assertEquals("2,310", plainNumber(BigDecimal("2310"), Locale.US))
        assertEquals("82.5", plainNumber(BigDecimal("82.50"), Locale.US))
        assertEquals("−1,997", signedNumber(BigDecimal("-1997"), Locale.US))
    }
}
