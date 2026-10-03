package com.repflow.app.presentation.home

import com.repflow.app.domain.recovery.ReadinessFactor
import com.repflow.app.domain.recovery.ReadinessFactorReading
import org.junit.Assert.assertEquals
import org.junit.Test

/** Functional review J1: the sheet's dots fill to the `v/5` the row states, not to how good the value is. */
class ReadinessFactorDotsTest {
    @Test
    fun `an inverted factor fills as many dots as its value`() {
        assertEquals(1, factorDotsFilled(ReadinessFactorReading(ReadinessFactor.LEG_DOMS, 1)))
        assertEquals(4, factorDotsFilled(ReadinessFactorReading(ReadinessFactor.PAIN_WHILE_WALKING, 4)))
    }

    @Test
    fun `a factor where high is good fills as many dots as its value`() {
        assertEquals(0, factorDotsFilled(ReadinessFactorReading(ReadinessFactor.SLEEP_QUALITY, 0)))
        assertEquals(5, factorDotsFilled(ReadinessFactorReading(ReadinessFactor.ENERGY, 5)))
    }
}
