package com.repflow.app.presentation.workout

import androidx.compose.runtime.saveable.SaverScope
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    fun `holding the numbers changes none of them and a later seed replaces nothing`() {
        val entry = SetEntryState()
        entry.applySeed(seed)

        entry.holdNumbers()

        assertEquals(BigDecimal("80"), entry.load)
        assertEquals(BigDecimal("8"), entry.reps)
        assertNull(entry.seconds)
        assertTrue(entry.touched)

        entry.applySeed(SetEntrySeed(load = BigDecimal("90"), reps = BigDecimal("10")))

        assertEquals(BigDecimal("80"), entry.load)
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

        entry.clearAfterSet(entry.submittedDetail())

        assertEquals(BigDecimal("60"), entry.load)
        assertEquals(BigDecimal("5"), entry.reps)
        assertEquals(BigDecimal("30"), entry.seconds)
        assertNull(entry.rpe)
        assertNull(entry.pain)
        assertNull(entry.technique)
        assertFalse(entry.isWarmup)
        assertTrue(entry.touched)
    }

    private fun entryWithAllDetail() =
        SetEntryState().apply {
            rpe = 7
            pain = 1
            technique = 4
            isWarmup = true
        }

    @Test
    fun `an RPE changed after the snapshot stays and the other fields still clear`() {
        val entry = entryWithAllDetail()
        val submitted = entry.submittedDetail()

        entry.rpe = 8
        entry.clearAfterSet(submitted)

        assertEquals(8, entry.rpe)
        assertNull(entry.pain)
        assertNull(entry.technique)
        assertFalse(entry.isWarmup)
    }

    @Test
    fun `a pain changed after the snapshot stays and the other fields still clear`() {
        val entry = entryWithAllDetail()
        val submitted = entry.submittedDetail()

        entry.pain = 2
        entry.clearAfterSet(submitted)

        assertNull(entry.rpe)
        assertEquals(2, entry.pain)
        assertNull(entry.technique)
        assertFalse(entry.isWarmup)
    }

    @Test
    fun `a technique changed after the snapshot stays and the other fields still clear`() {
        val entry = entryWithAllDetail()
        val submitted = entry.submittedDetail()

        entry.technique = 5
        entry.clearAfterSet(submitted)

        assertNull(entry.rpe)
        assertNull(entry.pain)
        assertEquals(5, entry.technique)
        assertFalse(entry.isWarmup)
    }

    @Test
    fun `fields empty in the snapshot and set afterwards stay, and so does a warm-up flag switched on afterwards`() {
        val entry = SetEntryState()
        val submitted = entry.submittedDetail()

        entry.rpe = 7
        entry.pain = 1
        entry.technique = 4
        entry.isWarmup = true
        entry.clearAfterSet(submitted)

        assertEquals(7, entry.rpe)
        assertEquals(1, entry.pain)
        assertEquals(4, entry.technique)
        assertTrue(entry.isWarmup)
    }

    @Test
    fun `fields cleared or switched off after the snapshot stay that way`() {
        val entry = entryWithAllDetail()
        val submitted = entry.submittedDetail()

        entry.rpe = null
        entry.pain = null
        entry.technique = null
        entry.isWarmup = false
        entry.clearAfterSet(submitted)

        assertNull(entry.rpe)
        assertNull(entry.pain)
        assertNull(entry.technique)
        assertFalse(entry.isWarmup)
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

    private fun entryRunningSince(start: Long = CLOCK_START) = SetEntryState().apply { startClock(start) }

    private fun entrySeededAt30() = SetEntryState().apply { applySeed(SetEntrySeed(seconds = BigDecimal("30"))) }

    @Test
    fun `the elapsed time comes from the start moment and the time given`() {
        val entry = entryRunningSince()

        assertEquals(0L, entry.elapsedSeconds(CLOCK_START + 999))
        assertEquals(1L, entry.elapsedSeconds(CLOCK_START + 1_000))
        assertEquals(90L, entry.elapsedSeconds(CLOCK_START + 90_500))
        assertEquals("the same call twice gives the same answer", 90L, entry.elapsedSeconds(CLOCK_START + 90_500))
        assertEquals("an earlier time reads earlier: nothing is counted", 1L, entry.elapsedSeconds(CLOCK_START + 1_000))
        assertEquals(CLOCK_START, entry.clockStartedAtMillis)
    }

    @Test
    fun `no running clock reads zero`() {
        assertEquals(0L, SetEntryState().elapsedSeconds(CLOCK_START))
    }

    @Test
    fun `stopping fills in the elapsed seconds and records them`() {
        val entry = entryRunningSince()

        entry.stopClock(CLOCK_START + 90_500)

        assertEquals(BigDecimal("90"), entry.seconds)
        assertTrue(entry.touched)
        assertNull(entry.clockStartedAtMillis)
        assertEquals(90L, entry.clockStoppedSeconds)
        assertTrue(entry.filledFromClock)
    }

    @Test
    fun `a seeded entry holds the stopped time and a later seed does not replace it`() {
        val entry = entrySeededAt30()
        entry.startClock(CLOCK_START)

        entry.stopClock(CLOCK_START + 90_500)
        assertEquals(BigDecimal("90"), entry.seconds)

        entry.applySeed(SetEntrySeed(seconds = BigDecimal("120")))
        assertEquals(BigDecimal("90"), entry.seconds)
    }

    @Test
    fun `a stop under a second clears the clock and leaves the entry as it was`() {
        val seeded = entrySeededAt30()
        seeded.startClock(CLOCK_START)
        seeded.stopClock(CLOCK_START + 999)

        assertEquals(BigDecimal("30"), seeded.seconds)
        assertFalse(seeded.touched)
        assertNull(seeded.clockStartedAtMillis)
        assertNull(seeded.clockStoppedSeconds)
        assertFalse(seeded.filledFromClock)

        val empty = entryRunningSince()
        empty.stopClock(CLOCK_START + 999)

        assertNull(empty.seconds)
        assertFalse(empty.touched)
        assertNull(empty.clockStartedAtMillis)
        assertNull(empty.clockStoppedSeconds)
    }

    @Test
    fun `a stop with no clock running changes nothing`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        entry.stopClock(CLOCK_START + 200_000)

        assertEquals(BigDecimal("90"), entry.seconds)
        assertEquals(90L, entry.clockStoppedSeconds)
        assertTrue(entry.filledFromClock)
    }

    @Test
    fun `a wall clock set back reads zero and stops without writing`() {
        val entry = entrySeededAt30()
        entry.startClock(CLOCK_START)

        assertEquals(0L, entry.elapsedSeconds(CLOCK_START - 5_000))

        entry.stopClock(CLOCK_START - 5_000)

        assertEquals(BigDecimal("30"), entry.seconds)
        assertFalse(entry.touched)
        assertNull(entry.clockStartedAtMillis)
        assertNull(entry.clockStoppedSeconds)
    }

    @Test
    fun `a stop after longer than the entry can hold fills in the largest it can`() {
        val entry = entryRunningSince()
        val elapsedMillis = (Int.MAX_VALUE.toLong() + 5) * 1_000

        entry.stopClock(CLOCK_START + elapsedMillis)

        assertEquals(BigDecimal(Int.MAX_VALUE), entry.seconds)
        assertEquals(Int.MAX_VALUE.toLong(), entry.clockStoppedSeconds)
        assertTrue(entry.filledFromClock)
    }

    @Test
    fun `Log set waits for Stop on a timed exercise even with seconds filled in`() {
        val entry = SetEntryState()
        entry.enterSeconds(BigDecimal("45"))
        assertTrue(entry.canLog(ExerciseTrackingType.DURATION))

        entry.startClock(CLOCK_START)
        assertFalse(entry.canLog(ExerciseTrackingType.DURATION))

        entry.stopClock(CLOCK_START + 60_000)
        assertTrue(entry.canLog(ExerciseTrackingType.DURATION))
    }

    @Test
    fun `a running clock blocks Log set whatever the tracking type, and the types' own rules are unchanged`() {
        val entry = SetEntryState()
        entry.enterLoad(BigDecimal("20"))
        entry.enterReps(BigDecimal("5"))
        assertTrue(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))
        assertTrue(entry.canLog(ExerciseTrackingType.REPS_ONLY))

        entry.startClock(CLOCK_START)

        assertFalse(entry.canLog(ExerciseTrackingType.WEIGHT_AND_REPS))
        assertFalse(entry.canLog(ExerciseTrackingType.REPS_ONLY))
    }

    @Test
    fun `starting a running clock keeps its first start moment`() {
        val entry = entryRunningSince()

        entry.startClock(CLOCK_START + 30_000)

        assertEquals(CLOCK_START, entry.clockStartedAtMillis)
        assertEquals(60L, entry.elapsedSeconds(CLOCK_START + 60_000))
    }

    @Test
    fun `starting after a stop forgets the stopped time and leaves the seconds until the next stop`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        entry.startClock(CLOCK_START + 100_000)

        assertNull(entry.clockStoppedSeconds)
        assertEquals(BigDecimal("90"), entry.seconds)
        assertFalse(entry.filledFromClock)

        entry.stopClock(CLOCK_START + 145_000)

        assertEquals(BigDecimal("45"), entry.seconds)
        assertEquals(45L, entry.clockStoppedSeconds)
    }

    @Test
    fun `entering seconds after a stop ends filled from the clock and keeps the stopped time`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        entry.enterSeconds(BigDecimal("100"))

        assertFalse(entry.filledFromClock)
        assertEquals(90L, entry.clockStoppedSeconds)
        assertEquals(BigDecimal("100"), entry.seconds)
    }

    @Test
    fun `the same value at another scale still counts as filled from the clock`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        entry.enterSeconds(BigDecimal("90.0"))

        assertTrue(entry.filledFromClock)
    }

    @Test
    fun `a saved set clears the stopped time it was logged with and leaves the seconds`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        entry.clearAfterSet(entry.submittedDetail())

        assertNull(entry.clockStoppedSeconds)
        assertEquals(BigDecimal("90"), entry.seconds)
        assertFalse(entry.filledFromClock)
    }

    @Test
    fun `a stopped time changed since the tap stays`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)
        val submitted = entry.submittedDetail()

        entry.startClock(CLOCK_START + 100_000)
        entry.stopClock(CLOCK_START + 145_000)
        entry.clearAfterSet(submitted)

        assertEquals(45L, entry.clockStoppedSeconds)
        assertTrue(entry.filledFromClock)
    }

    @Test
    fun `a clock started since the tap keeps running after the save`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)
        val submitted = entry.submittedDetail()

        entry.startClock(CLOCK_START + 100_000)
        entry.clearAfterSet(submitted)

        assertEquals(CLOCK_START + 100_000, entry.clockStartedAtMillis)
        assertNull(entry.clockStoppedSeconds)
    }

    @Test
    fun `the saved form keeps a running clock with its exact start moment`() {
        val entry = entrySeededAt30()
        entry.startClock(CLOCK_START)

        val restored = roundTrip(entry)

        assertEquals(CLOCK_START, restored.clockStartedAtMillis)
        assertEquals(60L, restored.elapsedSeconds(CLOCK_START + 60_000))
        assertNull(restored.clockStoppedSeconds)
        assertEquals(BigDecimal("30"), restored.seconds)
    }

    @Test
    fun `the saved form keeps a stopped time`() {
        val entry = entryRunningSince()
        entry.stopClock(CLOCK_START + 90_000)

        val restored = roundTrip(entry)

        assertNull(restored.clockStartedAtMillis)
        assertEquals(90L, restored.clockStoppedSeconds)
        assertTrue(restored.filledFromClock)
    }

    @Test
    fun `a list saved before the clock existed restores with neither value`() {
        val saved = listOf<Any?>("82.5", "8", null, 7, 1, 4, true, true)

        val restored = SetEntryState.fromSaved(saved)

        assertEquals(BigDecimal("82.5"), restored.load)
        assertEquals(BigDecimal("8"), restored.reps)
        assertEquals(7, restored.rpe)
        assertTrue(restored.isWarmup)
        assertTrue(restored.touched)
        assertNull(restored.clockStartedAtMillis)
        assertNull(restored.clockStoppedSeconds)
    }

    @Test
    fun `the holder's saver keeps each exercise's own clock`() {
        val holder = FocusEntries()
        holder.entryFor("plank", seed = null).startClock(CLOCK_START)
        holder.entryFor("side-plank", seed = null).run {
            startClock(CLOCK_START)
            stopClock(CLOCK_START + 90_000)
        }
        holder.entryFor("bench", seed = null).enterLoad(BigDecimal("60"))

        val scope = SaverScope { true }
        val saved = with(FocusEntries.Saver) { scope.save(holder) }
        val restored = requireNotNull(FocusEntries.Saver.restore(requireNotNull(saved)))

        val plank = restored.entryFor("plank", seed = null)
        assertEquals(CLOCK_START, plank.clockStartedAtMillis)
        assertNull(plank.clockStoppedSeconds)
        val sidePlank = restored.entryFor("side-plank", seed = null)
        assertNull(sidePlank.clockStartedAtMillis)
        assertEquals(90L, sidePlank.clockStoppedSeconds)
        val bench = restored.entryFor("bench", seed = null)
        assertNull(bench.clockStartedAtMillis)
        assertNull(bench.clockStoppedSeconds)
    }

    @Test
    fun `retaining keeps a listed exercise's entry with its running clock and drops the others`() {
        val holder = FocusEntries()
        val plank = holder.entryFor("plank", seed = null).also { it.startClock(CLOCK_START) }
        holder.entryFor("earlier-plank", seed = null).startClock(CLOCK_START)

        holder.retainOnly(setOf("plank"))

        assertSame(plank, holder.entryFor("plank", seed = null))
        assertEquals(CLOCK_START, plank.clockStartedAtMillis)
        assertNull(holder.entryFor("earlier-plank", seed = null).clockStartedAtMillis)
    }

    private companion object {
        const val CLOCK_START = 1_762_000_000_000L
    }
}
