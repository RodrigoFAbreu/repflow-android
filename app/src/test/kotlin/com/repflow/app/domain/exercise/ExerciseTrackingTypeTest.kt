package com.repflow.app.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the exact persisted string for every [ExerciseTrackingType] constant.
 *
 * Room persists this enum by [Enum.name] (see `data/exercise`), so an
 * accidental rename here would silently corrupt every stored row without
 * this test failing loudly at the source.
 */
class ExerciseTrackingTypeTest {
    @Test
    fun `persisted names are pinned`() {
        assertEquals("WEIGHT_AND_REPS", ExerciseTrackingType.WEIGHT_AND_REPS.name)
        assertEquals("REPS_ONLY", ExerciseTrackingType.REPS_ONLY.name)
        assertEquals("DURATION", ExerciseTrackingType.DURATION.name)
    }

    @Test
    fun `only weight and reps supports a load increment`() {
        assertTrue(ExerciseTrackingType.WEIGHT_AND_REPS.supportsLoad)
        assertFalse(ExerciseTrackingType.REPS_ONLY.supportsLoad)
        assertFalse(ExerciseTrackingType.DURATION.supportsLoad)
    }

    @Test
    fun `only the three approved Option A values exist`() {
        assertEquals(
            setOf("WEIGHT_AND_REPS", "REPS_ONLY", "DURATION"),
            ExerciseTrackingType.entries.map { it.name }.toSet(),
        )
    }
}
