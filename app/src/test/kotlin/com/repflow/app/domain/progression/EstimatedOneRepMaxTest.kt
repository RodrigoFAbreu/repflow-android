package com.repflow.app.domain.progression

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class EstimatedOneRepMaxTest {
    private fun estimate(
        load: String,
        reps: Int,
    ): BigDecimal? = EstimatedOneRepMax.brzycki(BigDecimal(load), reps)

    @Test
    fun `reproduces the design's own sample - 82_5 kg for 7 reps estimates 99 kg`() {
        assertEquals(0, BigDecimal("99").compareTo(estimate("82.5", 7)))
    }

    @Test
    fun `a single rep is the load itself`() {
        assertEquals(0, BigDecimal("100").compareTo(estimate("100", 1)))
    }

    @Test
    fun `is Brzycki - load times 36 over 37 minus reps`() {
        // 100 × 36 / 27 = 133.3333...
        assertEquals(BigDecimal("133.3333"), estimate("100", 10))
        // 60 × 36 / 25 = 86.4
        assertEquals(0, BigDecimal("86.4").compareTo(estimate("60", 12)))
    }

    @Test
    fun `is undefined beyond the rep ceiling, for zero reps, and for a negative load`() {
        assertNull(estimate("60", EstimatedOneRepMax.MAX_REPS + 1))
        assertNull(estimate("60", 0))
        assertNull(estimate("-1", 5))
    }

    @Test
    fun `a zero load estimates zero`() {
        assertEquals(0, BigDecimal.ZERO.compareTo(estimate("0", 5)))
    }
}
