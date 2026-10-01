package com.repflow.app.presentation.workout

import com.repflow.app.domain.workout.WorkoutExerciseId

/*
 * The workout board's plain-value derivations (remediation-1 CP7, `4a`
 * `nBoard`), kept out of the composables so a JVM test can pin them.
 *
 * The prototype's rules (`statusOf`, `nextUnfinished`, `nProgress`,
 * `RepFlow.dc.html:3510-3527`, `:3956`), transcribed onto what the domain
 * actually stores:
 *
 * - **Only working sets count.** `done` is the number of non-warm-up sets,
 *   as the prototype's `done = sets.filter(!warm).length`.
 * - **A planned exercise's target is its plan's working-set count**
 *   ([PlannedTargetUi.targetWorkingSets]); it is finished once `done` reaches
 *   it.
 * - **An ad-hoc exercise has no target** - `WorkoutExercise` carries none
 *   outside a plan, where the prototype invents `target: 3`. Its chip counts
 *   what was logged, and it counts as finished once one working set is
 *   logged (deviation `D55`).
 * - **`up next` is the first unfinished exercise in board order**, a hint
 *   rather than a lock: any row opens focus mode.
 */

/** The board status chip's three states (`6b`): every one carries a word and a glyph. */
enum class BoardRowStatus {
    /** Nothing logged yet: `T sets` (planned) or `No sets yet` (ad hoc), `ph-circle`. */
    NOT_STARTED,

    /** Some working sets logged, target not reached: `d/T sets`, `ph-dot-outline`. */
    IN_PROGRESS,

    /** Target reached (or, ad hoc, at least one working set): `d/T sets` / `d sets`, `ph-check-fat`. */
    DONE,
}

/** One board row: what the row and its status chip render. */
data class BoardRowUi(
    val id: WorkoutExerciseId,
    val name: String,
    val status: BoardRowStatus,
    /** Working sets logged. */
    val workingSetsDone: Int,
    /** The planned working-set target; `null` for an ad-hoc exercise. */
    val targetWorkingSets: Int?,
    val isUpNext: Boolean,
)

/**
 * The progress line, `N of M exercises · S/T sets`. [targetSets] is `null`
 * when no exercise on the board has a planned target, in which case the
 * line reads `N of M exercises · S sets` - there is no total to divide by.
 */
data class BoardProgressUi(
    val exercisesDone: Int,
    val exerciseCount: Int,
    val setsDone: Int,
    val targetSets: Int?,
)

internal fun ActiveExerciseUi.workingSetsDone(): Int = sets.count { !it.isWarmup }

internal fun ActiveExerciseUi.isFinished(): Boolean {
    val done = workingSetsDone()
    val target = plannedTarget?.targetWorkingSets
    return if (target == null) done > 0 else done >= target
}

internal fun boardRows(exercises: List<ActiveExerciseUi>): List<BoardRowUi> {
    val upNextIndex = exercises.indexOfFirst { !it.isFinished() }
    return exercises.mapIndexed { index, exercise ->
        val done = exercise.workingSetsDone()
        BoardRowUi(
            id = exercise.id,
            name = exercise.name,
            status =
                when {
                    exercise.isFinished() -> BoardRowStatus.DONE
                    done > 0 -> BoardRowStatus.IN_PROGRESS
                    else -> BoardRowStatus.NOT_STARTED
                },
            workingSetsDone = done,
            targetWorkingSets = exercise.plannedTarget?.targetWorkingSets,
            isUpNext = index == upNextIndex,
        )
    }
}

/**
 * The total an ad-hoc exercise contributes to `T` is what it has logged: it
 * has no target of its own, so it neither adds outstanding work nor makes
 * the planned total overflow.
 */
internal fun boardProgress(exercises: List<ActiveExerciseUi>): BoardProgressUi {
    val anyPlanned = exercises.any { it.plannedTarget != null }
    return BoardProgressUi(
        exercisesDone = exercises.count { it.isFinished() },
        exerciseCount = exercises.size,
        setsDone = exercises.sumOf { it.workingSetsDone() },
        targetSets =
            if (anyPlanned) {
                exercises.sumOf { it.plannedTarget?.targetWorkingSets ?: it.workingSetsDone() }
            } else {
                null
            },
    )
}

/**
 * The picker sheet's search: a case-insensitive match on the exercise name.
 * The prototype also matches a muscle group, which the domain does not have
 * (`D8`).
 */
internal fun filterPickerItems(
    items: List<ExercisePickerItem>,
    query: String,
): List<ExercisePickerItem> {
    val needle = query.trim()
    return if (needle.isEmpty()) items else items.filter { it.name.contains(needle, ignoreCase = true) }
}

/**
 * One row of the finish sheet's unfinished list (remediation-1 CP9, `4a`
 * `nUnfinishedList`): `○ <name> … N sets left`. [setsLeft] is `null` for an
 * ad-hoc exercise, which has no target to count down from and is unfinished
 * only while it has no working set (`D55`) - its row reads `No sets yet`.
 */
data class UnfinishedExerciseUi(
    val id: WorkoutExerciseId,
    val name: String,
    val setsLeft: Int?,
)

/**
 * The finish sheet's unfinished list: every exercise the board does not
 * count as finished ([isFinished]), in board order - the prototype's
 * `nUnfinished`. Derived here, next to the board's own rule, so the sheet and
 * the board can never disagree about what is left.
 */
internal fun unfinishedExercises(exercises: List<ActiveExerciseUi>): List<UnfinishedExerciseUi> =
    exercises
        .filterNot { it.isFinished() }
        .map { exercise ->
            UnfinishedExerciseUi(
                id = exercise.id,
                name = exercise.name,
                setsLeft = exercise.plannedTarget?.targetWorkingSets?.let { it - exercise.workingSetsDone() },
            )
        }
