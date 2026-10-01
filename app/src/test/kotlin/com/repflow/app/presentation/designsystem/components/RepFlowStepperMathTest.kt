package com.repflow.app.presentation.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** Pins [RepFlowStepperMath]: exact decimal steps, clamped into the range. */
class RepFlowStepperMathTest {
    private val zero = BigDecimal.ZERO

    @Test
    fun aStepMovesByExactlyTheStepSize() {
        assertEquals(BigDecimal("85.0"), RepFlowStepperMath.step(BigDecimal("82.5"), BigDecimal("2.5"), 1, zero, null))
        assertEquals(BigDecimal("80.0"), RepFlowStepperMath.step(BigDecimal("82.5"), BigDecimal("2.5"), -1, zero, null))
        // Ten 0.1 steps land on 1.0 exactly, not 0.9999999.
        val ten = (1..10).fold(zero) { v, _ -> RepFlowStepperMath.step(v, BigDecimal("0.1"), 1, zero, null) }
        assertEquals(0, BigDecimal.ONE.compareTo(ten))
    }

    @Test
    fun stepsAreClampedIntoTheRange() {
        assertEquals(zero, RepFlowStepperMath.step(BigDecimal("1"), BigDecimal("2.5"), -1, zero, null))
        assertEquals(BigDecimal("10"), RepFlowStepperMath.step(BigDecimal("9"), BigDecimal("5"), 1, zero, BigDecimal("10")))
    }

    @Test
    fun nothingEnteredYetStepsFromTheMinimum() {
        assertEquals(BigDecimal("5"), RepFlowStepperMath.step(null, BigDecimal("5"), 1, zero, null))
        assertEquals(zero, RepFlowStepperMath.step(null, BigDecimal("5"), -1, zero, null))
    }

    @Test
    fun aTypedValueIsClampedIntoTheRange() {
        assertEquals(BigDecimal("10"), RepFlowStepperMath.coerce(BigDecimal("12"), zero, BigDecimal("10")))
        assertEquals(zero, RepFlowStepperMath.coerce(BigDecimal("-1"), zero, null))
        assertEquals(BigDecimal("7"), RepFlowStepperMath.coerce(BigDecimal("7"), zero, null))
    }

    @Test
    fun valuesFormatWithoutTrailingZerosOrExponents() {
        assertEquals("82.5", RepFlowStepperMath.format(BigDecimal("82.50")))
        assertEquals("80", RepFlowStepperMath.format(BigDecimal("80.0")))
        assertEquals("100", RepFlowStepperMath.format(BigDecimal("1E+2")))
    }
}
