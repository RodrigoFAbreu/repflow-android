package com.repflow.app.presentation.workout

import com.repflow.app.application.workout.DEFAULT_REST_TIMER_SECONDS
import com.repflow.app.domain.workout.WorkoutExerciseId
import java.math.BigDecimal

/*
 * Focus mode's plain-value derivations (remediation-1 CP8, `4a` `nFocus`),
 * kept out of the composables so a JVM test can pin them. The prototype's
 * rules (`nFocusPos`, `nFocusSets`, `nFocusRows`, `nextUnfinished`,
 * `RepFlow.dc.html:4115-4141`, `:3520-3527`) on what the domain stores - the
 * same "only working sets count" and "an ad-hoc exercise has no target" rules
 * the board uses (`WorkoutBoardModel.kt`, `D55`).
 */

/** One row of focus mode's set list: a logged set, or a planned working set not logged yet. */
internal sealed interface FocusSetRow {
    /**
     * A logged set. [isExtra] is the set-classification marker
     * ([setsWithExtraFlag]); [isLast] marks the one set a correction can reach
     * - undo and edit stay on the most recently recorded set (`D31`).
     */
    data class Logged(
        val set: ActiveSetUi,
        val isExtra: Boolean,
        val isLast: Boolean,
    ) : FocusSetRow

    /**
     * A planned working set still to log, numbered on from the logged ones.
     * This is how the design shows the target without a chip of its own.
     */
    data class Pending(
        val setNumber: Int,
    ) : FocusSetRow
}

/** The header: `Exercise N of M`, then `X of Y sets done` (and the warm-ups when the plan has any). */
internal data class FocusHeaderUi(
    val position: Int,
    val exerciseCount: Int,
    val workingSetsDone: Int,
    /** `null` for an ad-hoc exercise, which has no target (`D55`). */
    val workingSetTarget: Int?,
    val warmupSetsDone: Int,
    /** `null` when the plan sets no warm-ups, and always for an ad-hoc exercise. */
    val warmupSetTarget: Int?,
)

internal fun focusHeader(
    exercises: List<ActiveExerciseUi>,
    exercise: ActiveExerciseUi,
): FocusHeaderUi =
    FocusHeaderUi(
        position = exercises.indexOfFirst { it.id == exercise.id } + 1,
        exerciseCount = exercises.size,
        workingSetsDone = exercise.workingSetsDone(),
        workingSetTarget = exercise.plannedTarget?.targetWorkingSets,
        warmupSetsDone = exercise.sets.count { it.isWarmup },
        warmupSetTarget = exercise.plannedTarget?.targetWarmupSets?.takeIf { it > 0 },
    )

/**
 * The logged sets in order, then one [FocusSetRow.Pending] per planned
 * working set not logged yet. An ad-hoc exercise has no target, so it has no
 * pending rows.
 */
internal fun ActiveExerciseUi.focusSetRows(): List<FocusSetRow> {
    val logged =
        setsWithExtraFlag().mapIndexed { index, (set, isExtra) ->
            FocusSetRow.Logged(set = set, isExtra = isExtra, isLast = index == sets.lastIndex)
        }
    val target = plannedTarget?.targetWorkingSets ?: return logged
    val pending = (target - workingSetsDone()).coerceAtLeast(0)
    val lastNumber = sets.maxOfOrNull { it.setNumber } ?: 0
    return logged + (1..pending).map { FocusSetRow.Pending(setNumber = lastNumber + it) }
}

/**
 * `Next ›`'s target: the next unfinished exercise after [currentId] in board
 * order, wrapping round and reaching [currentId] itself last - the prototype's
 * `nextUnfinished`. `null` when every exercise is finished, in which case
 * `Next ›` returns to the board.
 */
internal fun nextUnfinishedExercise(
    exercises: List<ActiveExerciseUi>,
    currentId: WorkoutExerciseId,
): WorkoutExerciseId? {
    val from = exercises.indexOfFirst { it.id == currentId }
    if (exercises.isEmpty()) return null
    return (1..exercises.size)
        .map { step -> exercises[(from + step).mod(exercises.size)] }
        .firstOrNull { !it.isFinished() }
        ?.id
}

/**
 * The rest that follows a set of this exercise, for the warm-up hint: the
 * plan's rest, else the app's default - exactly the rule
 * `ActiveWorkoutViewModel.onRecordSet` applies, warm-up or not (`D63`).
 */
internal fun ActiveExerciseUi.restSecondsAfterSet(): Int = plannedTarget?.restSeconds ?: DEFAULT_REST_TIMER_SECONDS

/** The weight stepper's step: the exercise's load increment, else the design's own 2.5 kg (`D61`). */
internal fun ActiveExerciseUi.loadStep(): BigDecimal = defaultLoadIncrement ?: DEFAULT_LOAD_STEP_KG

/** `4a`'s `kg · 2.5 steps` - the prototype's only step, used when the exercise sets none. */
internal val DEFAULT_LOAD_STEP_KG: BigDecimal = BigDecimal("2.5")

/**
 * Pairs each set with whether it falls beyond its planned warm-up/working
 * quota (Milestone 8, implementation-review finding #2) - `false` for every
 * set on an ad-hoc exercise ([ActiveExerciseUi.plannedTarget] is `null`).
 *
 * `internal` rather than private so a plain-JVM test can reach it: it decides
 * content rather than appearance. Moved here from the flat exercise card that
 * focus mode replaced (remediation-1 CP8); the `extra` marker it feeds is a
 * preserved set-classification invariant.
 */
internal fun ActiveExerciseUi.setsWithExtraFlag(): List<Pair<ActiveSetUi, Boolean>> {
    val target = plannedTarget ?: return sets.map { it to false }
    var warmupSeen = 0
    var workingSeen = 0
    return sets.map { set ->
        val isExtra =
            if (set.isWarmup) {
                warmupSeen += 1
                warmupSeen > (target.targetWarmupSets ?: 0)
            } else {
                workingSeen += 1
                workingSeen > target.targetWorkingSets
            }
        set to isExtra
    }
}
