package com.repflow.app.presentation.workout

import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.designsystem.components.RepFlowButtonDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the Active Workout set-entry/rest-timer surface's own non-composable
 * wiring - the parts of this checkpoint a device test and a screenshot both
 * look straight past.
 *
 * `ActiveWorkoutScreenTest` in `androidTest` already proves the tracking-type
 * fields render and that recording a set clears them. What it cannot see is
 * the rest strip at all (it never constructs a `RestTimerUi`), so the
 * progress fraction this checkpoint introduces - the one new derivation in
 * CP6, and the one place a divide-by-zero or an unclamped ratio would sit -
 * has no other coverage. `setsWithExtraFlag` is likewise pure content logic
 * that decides what each logged row says, not how it looks.
 *
 * The sizes are here for the same reason CP1's token hexes are pinned: they
 * are transcriptions of the design, and nothing else would catch a tap target
 * that ends up merely plausible.
 */
class ActiveWorkoutScreenWiringTest {
    private fun set(
        setNumber: Int,
        isWarmup: Boolean = false,
    ) = ActiveSetUi(
        id = WorkoutSetId("set-$setNumber"),
        setNumber = setNumber,
        load = null,
        reps = null,
        durationSeconds = null,
        isWarmup = isWarmup,
    )

    private fun exercise(
        sets: List<ActiveSetUi>,
        plannedTarget: PlannedTargetUi?,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId("exercise-1"),
        name = "Bench Press",
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        sets = sets,
        plannedTarget = plannedTarget,
    )

    @Test
    fun aFullRestTimerReadsFullAndAnExpiredOneReadsEmpty() {
        assertEquals(1f, restTimerProgress(remainingSeconds = 90, totalDurationSeconds = 90), 0f)
        assertEquals(0f, restTimerProgress(remainingSeconds = 0, totalDurationSeconds = 90), 0f)
    }

    @Test
    fun aPartlyElapsedRestTimerReadsItsRemainingFraction() {
        assertEquals(0.5f, restTimerProgress(remainingSeconds = 45, totalDurationSeconds = 90), 0.0001f)
    }

    /**
     * `+15s` can push the remaining time past the total the timer started
     * with, and a progress bar handed 1.4 would either clip or wrap.
     */
    @Test
    fun addingRestBeyondTheOriginalTotalStaysClampedToFull() {
        assertEquals(1f, restTimerProgress(remainingSeconds = 120, totalDurationSeconds = 90), 0f)
    }

    /** A timer with no recorded total reads as elapsed, never divides by zero. */
    @Test
    fun aTimerWithNoRecordedTotalReadsAsElapsed() {
        assertEquals(0f, restTimerProgress(remainingSeconds = 30, totalDurationSeconds = 0), 0f)
        assertEquals(0f, restTimerProgress(remainingSeconds = 30, totalDurationSeconds = -1), 0f)
    }

    @Test
    fun everySetOnAnAdHocExerciseIsWithinQuota() {
        val flags = exercise(sets = listOf(set(1), set(2), set(3)), plannedTarget = null).setsWithExtraFlag()

        assertEquals(listOf(false, false, false), flags.map { it.second })
    }

    /**
     * Warm-up and working quotas are counted separately: a third warm-up set
     * against a target of two is extra even while working sets are still
     * short of their own target.
     */
    @Test
    fun warmupAndWorkingQuotasAreCountedAgainstTheirOwnTargets() {
        val target = PlannedTargetUi(targetWarmupSets = 2, targetWorkingSets = 2)
        val sets = listOf(set(1, isWarmup = true), set(2, isWarmup = true), set(3, isWarmup = true), set(4))

        val flags = exercise(sets = sets, plannedTarget = target).setsWithExtraFlag()

        assertEquals(listOf(false, false, true, false), flags.map { it.second })
    }

    /** A plan with no warm-up target makes the first warm-up set an extra. */
    @Test
    fun aWarmupSetAgainstAPlanWithNoWarmupTargetIsExtra() {
        val target = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 3)
        val flags =
            exercise(sets = listOf(set(1, isWarmup = true), set(2)), plannedTarget = target).setsWithExtraFlag()

        assertEquals(listOf(true, false), flags.map { it.second })
    }

    /** The design's own floor: every tap target on this surface is at least 44. */
    @Test
    fun everyTapTargetOnTheSetEntrySurfaceClearsTheFloor() {
        val minimum = ControlRowMinHeight
        assertTrue(RepFlowButtonDefaults.primaryMinHeight >= minimum)
        assertTrue(RepFlowButtonDefaults.neutralOutlineMinHeight >= minimum)
        assertEquals(minimum, RepFlowButtonDefaults.neutralOutlineMinHeight)
    }
}
