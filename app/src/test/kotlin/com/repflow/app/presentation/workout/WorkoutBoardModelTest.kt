package com.repflow.app.presentation.workout

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSetId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The workout board's derivations (remediation-1 CP7): the status chip's
 * state per row, `up next`, the progress line, and the picker sheet's
 * search - the prototype's `statusOf` / `nextUnfinished` / `nProgress` rules
 * on what the domain stores.
 */
class WorkoutBoardModelTest {
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
        target: Int? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId(id),
        name = id,
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        sets = sets,
        plannedTarget = target?.let { PlannedTargetUi(targetWarmupSets = 1, targetWorkingSets = it) },
    )

    @Test
    fun aPlannedRowIsNotStartedThenInProgressThenDoneAndWarmupsNeverCount() {
        val rows =
            boardRows(
                listOf(
                    exercise("a", sets = listOf(set(isWarmup = true)), target = 3),
                    exercise("b", sets = listOf(set(isWarmup = true), set()), target = 3),
                    exercise("c", sets = listOf(set(), set(), set()), target = 3),
                    exercise("d", sets = listOf(set(), set(), set(), set()), target = 3),
                ),
            )

        assertEquals(
            listOf(BoardRowStatus.NOT_STARTED, BoardRowStatus.IN_PROGRESS, BoardRowStatus.DONE, BoardRowStatus.DONE),
            rows.map { it.status },
        )
        assertEquals(listOf(0, 1, 3, 4), rows.map { it.workingSetsDone })
        assertEquals(listOf(3, 3, 3, 3), rows.map { it.targetWorkingSets })
    }

    @Test
    fun anAdHocRowHasNoTargetAndIsDoneOnceOneWorkingSetIsLogged() {
        val rows =
            boardRows(
                listOf(
                    exercise("empty"),
                    exercise("warmup-only", sets = listOf(set(isWarmup = true))),
                    exercise("logged", sets = listOf(set(), set())),
                ),
            )

        assertEquals(
            listOf(BoardRowStatus.NOT_STARTED, BoardRowStatus.NOT_STARTED, BoardRowStatus.DONE),
            rows.map { it.status },
        )
        rows.forEach { assertNull(it.targetWorkingSets) }
    }

    @Test
    fun upNextIsTheFirstUnfinishedRowWhereverItSits() {
        val rows =
            boardRows(
                listOf(
                    exercise("done", sets = listOf(set(), set()), target = 2),
                    exercise("next", target = 2),
                    exercise("later", sets = listOf(set()), target = 2),
                ),
            )

        assertEquals(listOf(false, true, false), rows.map { it.isUpNext })
    }

    @Test
    fun nothingIsUpNextOnceEveryRowIsFinished() {
        val rows = boardRows(listOf(exercise("a", sets = listOf(set()), target = 1), exercise("b", sets = listOf(set()))))

        assertEquals(listOf(false, false), rows.map { it.isUpNext })
    }

    @Test
    fun progressSumsWorkingSetsAgainstPlannedTargetsAndAdHocWorkAsLogged() {
        val progress =
            boardProgress(
                listOf(
                    exercise("planned-done", sets = listOf(set(), set(), set()), target = 3),
                    exercise("planned-open", sets = listOf(set(isWarmup = true), set()), target = 4),
                    exercise("ad-hoc", sets = listOf(set(), set())),
                ),
            )

        assertEquals(BoardProgressUi(exercisesDone = 2, exerciseCount = 3, setsDone = 6, targetSets = 9), progress)
    }

    @Test
    fun aBoardWithNoPlannedExerciseHasNoSetTotal() {
        assertEquals(
            BoardProgressUi(exercisesDone = 1, exerciseCount = 2, setsDone = 1, targetSets = null),
            boardProgress(listOf(exercise("a", sets = listOf(set())), exercise("b"))),
        )
        assertEquals(BoardProgressUi(0, 0, 0, null), boardProgress(emptyList()))
    }

    @Test
    fun pickerSearchMatchesNamesCaseInsensitivelyAndIgnoresSurroundingSpace() {
        val items =
            listOf("Back Squat", "Bench Press", "Front Squat").map {
                ExercisePickerItem(id = ExerciseId(it), name = it, trackingType = ExerciseTrackingType.WEIGHT_AND_REPS)
            }

        assertEquals(listOf("Back Squat", "Front Squat"), filterPickerItems(items, "  squat ").map { it.name })
        assertEquals(items, filterPickerItems(items, "   "))
        assertEquals(emptyList<ExercisePickerItem>(), filterPickerItems(items, "deadlift"))
    }

    /**
     * The finish sheet's unfinished list (remediation-1 CP9): exactly the rows
     * the board does not count as finished, in board order - a planned one
     * with the working sets it still owes, an ad-hoc one with nothing logged
     * as `null` (`No sets yet`, `D55`). Warm-ups pay nothing off.
     */
    @Test
    fun theUnfinishedListIsEveryExerciseTheBoardDoesNotCountAsFinished() {
        val unfinished =
            unfinishedExercises(
                listOf(
                    exercise("done", sets = listOf(set(), set()), target = 2),
                    exercise("partial", sets = listOf(set(isWarmup = true), set()), target = 3),
                    exercise("ad-hoc-empty", sets = listOf(set(isWarmup = true))),
                    exercise("ad-hoc-logged", sets = listOf(set())),
                ),
            )

        assertEquals(listOf("partial", "ad-hoc-empty"), unfinished.map { it.name })
        assertEquals(listOf(2, null), unfinished.map { it.setsLeft })
    }
}
