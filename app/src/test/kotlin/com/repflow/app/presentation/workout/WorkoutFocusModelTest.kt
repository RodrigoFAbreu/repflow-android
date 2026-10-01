package com.repflow.app.presentation.workout

import com.repflow.app.application.workout.DEFAULT_REST_TIMER_SECONDS
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Focus mode's derivations (remediation-1 CP8): the set list with its
 * not-yet-logged rows, the header counts, `Next ›`'s target, the rest the
 * warm-up hint states, and the weight stepper's step.
 */
class WorkoutFocusModelTest {
    private var setCounter = 0

    private fun set(isWarmup: Boolean = false): ActiveSetUi {
        setCounter += 1
        return ActiveSetUi(
            id = WorkoutSetId("set-$setCounter"),
            setNumber = setCounter,
            load = 60.0,
            reps = 8,
            durationSeconds = null,
            isWarmup = isWarmup,
        )
    }

    private fun exercise(
        id: String,
        sets: List<ActiveSetUi> = emptyList(),
        target: PlannedTargetUi? = null,
        defaultLoadIncrement: BigDecimal? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId(id),
        name = id,
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        sets = sets,
        plannedTarget = target,
        defaultLoadIncrement = defaultLoadIncrement,
    )

    private fun target(
        working: Int,
        warmup: Int? = null,
        rest: Int? = null,
    ) = PlannedTargetUi(targetWarmupSets = warmup, targetWorkingSets = working, repRange = 8..12, restSeconds = rest)

    @Test
    fun aPlannedExerciseListsItsLoggedSetsThenOnePendingRowPerWorkingSetStillToLog() {
        val warmup = set(isWarmup = true)
        val working = set()
        val rows = exercise("a", sets = listOf(warmup, working), target = target(working = 3, warmup = 1)).focusSetRows()

        assertEquals(
            listOf(
                FocusSetRow.Logged(warmup, isExtra = false, isLast = false),
                FocusSetRow.Logged(working, isExtra = false, isLast = true),
                FocusSetRow.Pending(setNumber = 3),
                FocusSetRow.Pending(setNumber = 4),
            ),
            rows,
        )
    }

    @Test
    fun anAdHocExerciseHasNoPendingRowsAndNothingIsExtra() {
        val rows = exercise("a", sets = listOf(set(), set())).focusSetRows()

        assertEquals(2, rows.size)
        assertEquals(listOf(false, false), rows.map { (it as FocusSetRow.Logged).isExtra })
    }

    @Test
    fun aSetBeyondTheTargetIsMarkedExtraAndLeavesNoPendingRow() {
        val rows = exercise("a", sets = listOf(set(), set()), target = target(working = 1)).focusSetRows()

        assertEquals(listOf(false, true), rows.map { (it as FocusSetRow.Logged).isExtra })
    }

    @Test
    fun theHeaderCountsWorkingSetsAgainstTheTargetAndWarmupsSeparately() {
        val a = exercise("a")
        val b = exercise("b", sets = listOf(set(isWarmup = true), set()), target = target(working = 3, warmup = 2))
        val c = exercise("c", sets = listOf(set()))

        assertEquals(
            FocusHeaderUi(
                position = 2,
                exerciseCount = 3,
                workingSetsDone = 1,
                workingSetTarget = 3,
                warmupSetsDone = 1,
                warmupSetTarget = 2,
            ),
            focusHeader(listOf(a, b, c), b),
        )
        // Ad hoc: no target at all, so the header can only count (`D55`).
        val adHoc = focusHeader(listOf(a, b, c), c)
        assertNull(adHoc.workingSetTarget)
        assertNull(adHoc.warmupSetTarget)
        // A plan with zero warm-ups shows no warm-up count.
        assertNull(focusHeader(listOf(a), exercise("a", target = target(working = 3, warmup = 0))).warmupSetTarget)
    }

    @Test
    fun nextGoesToTheNextUnfinishedExerciseWrappingRoundAndEndsOnTheBoardWhenAllAreFinished() {
        val done = exercise("done", sets = listOf(set()), target = target(working = 1))
        val first = exercise("first", target = target(working = 2))
        val current = exercise("current", target = target(working = 2))
        val later = exercise("later", target = target(working = 2))
        val exercises = listOf(first, done, current, later)

        assertEquals(later.id, nextUnfinishedExercise(exercises, current.id))
        assertEquals(first.id, nextUnfinishedExercise(exercises, later.id))
        // The only unfinished exercise left is the current one: Next stays on it.
        assertEquals(current.id, nextUnfinishedExercise(listOf(done, current), current.id))
        assertNull(nextUnfinishedExercise(listOf(done), done.id))
    }

    @Test
    fun theWarmupHintStatesThePlannedRestElseTheDefault() {
        assertEquals(45, exercise("a", target = target(working = 3, rest = 45)).restSecondsAfterSet())
        assertEquals(DEFAULT_REST_TIMER_SECONDS, exercise("b").restSecondsAfterSet())
    }

    @Test
    fun theWeightStepsByTheExercisesOwnIncrementElseTwoAndAHalfKilograms() {
        assertEquals(BigDecimal("1.25"), exercise("a", defaultLoadIncrement = BigDecimal("1.25")).loadStep())
        assertEquals(BigDecimal("2.5"), exercise("b").loadStep())
    }
}
