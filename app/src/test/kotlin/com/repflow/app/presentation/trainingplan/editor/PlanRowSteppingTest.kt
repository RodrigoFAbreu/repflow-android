package com.repflow.app.presentation.trainingplan.editor

import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.domain.trainingplan.TargetSets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/** The plan editor's stepper arithmetic (remediation-1 CP11). */
class PlanRowSteppingTest {
    @Test
    fun `a step moves by the line's own step size`() {
        assertEquals("4", PlanRowStepping.stepped("3", 1, PlanRowStepping.workingSets))
        assertEquals("2", PlanRowStepping.stepped("3", -1, PlanRowStepping.workingSets))
        assertEquals("35", PlanRowStepping.stepped("30", 1, PlanRowStepping.durationMin))
        assertEquals("105", PlanRowStepping.stepped("90", 1, PlanRowStepping.rest))
        assertEquals("75", PlanRowStepping.stepped("90", -1, PlanRowStepping.rest))
    }

    @Test
    fun `a step never leaves the domain's bounds`() {
        assertEquals(TargetSets.MIN.toString(), PlanRowStepping.stepped("1", -1, PlanRowStepping.workingSets))
        assertEquals(TargetSets.MAX.toString(), PlanRowStepping.stepped("20", 1, PlanRowStepping.workingSets))
        assertEquals("0", PlanRowStepping.stepped("0", -1, PlanRowStepping.warmupSets))
        assertEquals(RestDuration.MIN_SECONDS.toString(), PlanRowStepping.stepped("10", -1, PlanRowStepping.rest))
        assertEquals(RestDuration.MAX_SECONDS.toString(), PlanRowStepping.stepped("1795", 1, PlanRowStepping.rest))
    }

    @Test
    fun `an empty or unparsable field starts at the line's default instead of moving`() {
        assertEquals("3", PlanRowStepping.stepped("", 1, PlanRowStepping.workingSets))
        assertEquals("1", PlanRowStepping.stepped("  ", -1, PlanRowStepping.warmupSets))
        assertEquals("8", PlanRowStepping.stepped("abc", 1, PlanRowStepping.repMin))
        assertEquals("12", PlanRowStepping.stepped("", 1, PlanRowStepping.repMax))
        assertEquals("60", PlanRowStepping.stepped("", 1, PlanRowStepping.durationMax))
        assertEquals("90", PlanRowStepping.stepped("", -1, PlanRowStepping.rest))
    }

    @Test
    fun `a keypad entry is rounded to a whole number and clamped`() {
        assertEquals("5", PlanRowStepping.typed(BigDecimal("5"), PlanRowStepping.workingSets))
        assertEquals("8", PlanRowStepping.typed(BigDecimal("7.5"), PlanRowStepping.repMin))
        assertEquals(TargetSets.MAX.toString(), PlanRowStepping.typed(BigDecimal("99"), PlanRowStepping.workingSets))
        assertEquals("1", PlanRowStepping.typed(BigDecimal("0"), PlanRowStepping.rest))
    }

    @Test
    fun `the rest presets are 2a's six`() {
        assertEquals(listOf(60, 75, 90, 120, 150, 180), PlanRowStepping.restPresetSeconds)
    }

    @Test
    fun `the working-set total counts every parsable row and ignores one still being typed`() {
        val rows =
            listOf(
                PlannedExerciseRowUiState(rowId = 1L, targetSetsText = "3"),
                PlannedExerciseRowUiState(rowId = 2L, targetSetsText = " 4 "),
                PlannedExerciseRowUiState(rowId = 3L, targetSetsText = ""),
            )

        assertEquals(7, plannedWorkingSetTotal(rows))
    }
}
