package com.repflow.app.presentation.workout

import androidx.compose.runtime.saveable.SaverScope
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Remediation-1-remediation-1 CP9 (B3): the entry keeps its numbers, seeds only while untouched, and saves its flag. */
class SetEntryStateTest {
    private val seed = SetEntrySeed(load = BigDecimal("80"), reps = BigDecimal("8"))

    private fun roundTrip(state: SetEntryState): SetEntryState {
        val scope = SaverScope { true }
        val saved = with(SetEntryState.Saver) { scope.save(state) }
        return requireNotNull(SetEntryState.Saver.restore(requireNotNull(saved)))
    }

    @Test
    fun `an untouched entry takes the seed, and a later seed replaces it`() {
        val entry = SetEntryState()

        entry.applySeed(seed)
        assertEquals(BigDecimal("80"), entry.load)
        assertEquals(BigDecimal("8"), entry.reps)
        assertFalse(entry.touched)

        entry.applySeed(SetEntrySeed(load = BigDecimal("85"), reps = BigDecimal("5")))
        assertEquals(BigDecimal("85"), entry.load)
    }

    @Test
    fun `typing or stepping marks it touched and the seed never overwrites it again`() {
        val entry = SetEntryState()
        entry.applySeed(seed)

        entry.enterLoad(BigDecimal("82.5"))
        entry.applySeed(SetEntrySeed(load = BigDecimal("90"), reps = BigDecimal("10")))

        assertTrue(entry.touched)
        assertEquals(BigDecimal("82.5"), entry.load)
        assertEquals(BigDecimal("8"), entry.reps)
    }

    @Test
    fun `a null seed leaves the entry alone`() {
        val entry = SetEntryState()
        entry.applySeed(seed)

        entry.applySeed(null)

        assertEquals(BigDecimal("80"), entry.load)
    }

    @Test
    fun `clearAfterSet keeps weight, reps, seconds and the flag, and clears the rest`() {
        val entry = SetEntryState()
        entry.enterLoad(BigDecimal("60"))
        entry.enterReps(BigDecimal("5"))
        entry.enterSeconds(BigDecimal("30"))
        entry.rpe = 8
        entry.pain = 1
        entry.technique = 4
        entry.isWarmup = true

        entry.clearAfterSet()

        assertEquals(BigDecimal("60"), entry.load)
        assertEquals(BigDecimal("5"), entry.reps)
        assertEquals(BigDecimal("30"), entry.seconds)
        assertNull(entry.rpe)
        assertNull(entry.pain)
        assertNull(entry.technique)
        assertFalse(entry.isWarmup)
        assertTrue(entry.touched)
    }

    @Test
    fun `the saver round trip keeps every field including the touched flag`() {
        val entry = SetEntryState()
        entry.enterLoad(BigDecimal("82.5"))
        entry.enterReps(BigDecimal("8"))
        entry.rpe = 7
        entry.isWarmup = true

        val restored = roundTrip(entry)

        assertEquals(BigDecimal("82.5"), restored.load)
        assertEquals(BigDecimal("8"), restored.reps)
        assertEquals(7, restored.rpe)
        assertTrue(restored.isWarmup)
        assertTrue(restored.touched)
    }

    @Test
    fun `an untouched entry restores untouched and still takes a late seed`() {
        val entry = SetEntryState()
        entry.applySeed(seed)

        val restored = roundTrip(entry)
        assertFalse(restored.touched)
        restored.applySeed(SetEntrySeed(load = BigDecimal("85"), reps = BigDecimal("6")))

        assertEquals(BigDecimal("85"), restored.load)
    }

    @Test
    fun `Log set needs weight and reps for weight and reps, and an empty weight does not count`() {
        val entry = SetEntryState()
        assertFalse(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))

        entry.enterReps(BigDecimal("5"))
        assertFalse("reps alone are not enough", entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))

        entry.enterLoad(BigDecimal("20"))
        assertTrue(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))

        val weightOnly = SetEntryState()
        weightOnly.enterLoad(BigDecimal("20"))
        assertFalse("weight alone is not enough", weightOnly.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))
    }

    @Test
    fun `0 kg is an explicit weight and counts`() {
        val entry = SetEntryState()
        entry.enterLoad(BigDecimal.ZERO)
        assertFalse(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))
        entry.enterReps(BigDecimal("7"))
        assertTrue(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))
    }

    @Test
    fun `reps only needs reps and a timed exercise needs seconds, as before`() {
        val entry = SetEntryState()
        assertFalse(entry.canLog(ExerciseTrackingType.REPS_ONLY))
        entry.enterReps(BigDecimal("5"))
        assertTrue(entry.canLog(ExerciseTrackingType.REPS_ONLY))
        assertFalse(entry.canLog(ExerciseTrackingType.DURATION))
        entry.enterSeconds(BigDecimal("45"))
        assertTrue(entry.canLog(ExerciseTrackingType.DURATION))
    }
}
