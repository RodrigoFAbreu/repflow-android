package com.repflow.app.presentation.trainingplan.editor

import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.domain.trainingplan.DurationTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One plan-row stepper's arithmetic (remediation-1 CP11): its step, the
 * domain's own bounds, and the value a first tap lands on when the field is
 * still empty. Pure, so a JVM test pins it.
 */
internal data class PlanStepperSpec(
    val step: Int,
    val min: Int,
    val max: Int,
    val startWhenEmpty: Int,
)

/**
 * The plan editor's steppers. Each one reads and writes the row's existing
 * text field through the ViewModel's unchanged `String` setters, so a stepped
 * or keypad-typed value is validated exactly as a typed one always was.
 *
 * Steps follow `4a`'s plan edit (`RepFlow.dc.html:3670-3677`): sets and reps
 * by 1, a duration by 5 s, rest by 15 s. Bounds are the domain's own -
 * [TargetSets], [RepRange], [DurationTarget], [RestDuration] - so a stepper can
 * never produce a value the domain refuses. An empty field's first tap lands
 * on `2a`'s new-row defaults (`:4502-4505`): 3 sets, 8-12 reps, 30-60 s; rest
 * starts at 1:30, the middle of `2a`'s presets.
 */
internal object PlanRowStepping {
    val workingSets = PlanStepperSpec(step = 1, min = TargetSets.MIN, max = TargetSets.MAX, startWhenEmpty = 3)
    val warmupSets = PlanStepperSpec(step = 1, min = 0, max = TargetSets.MAX, startWhenEmpty = 1)
    val repMin = PlanStepperSpec(step = 1, min = RepRange.MIN_REPS, max = RepRange.MAX_REPS, startWhenEmpty = 8)
    val repMax = PlanStepperSpec(step = 1, min = RepRange.MIN_REPS, max = RepRange.MAX_REPS, startWhenEmpty = 12)
    val durationMin =
        PlanStepperSpec(
            step = 5,
            min = DurationTarget.MIN_SECONDS.toInt(),
            max = DurationTarget.MAX_SECONDS.toInt(),
            startWhenEmpty = 30,
        )
    val durationMax = durationMin.copy(startWhenEmpty = 60)
    val rest =
        PlanStepperSpec(
            step = 15,
            min = RestDuration.MIN_SECONDS.toInt(),
            max = RestDuration.MAX_SECONDS.toInt(),
            startWhenEmpty = 90,
        )

    /** `2a`'s rest presets (`:4541`): 1:00 1:15 1:30 2:00 2:30 3:00. */
    val restPresetSeconds: List<Int> = REST_PRESET_SECONDS

    /**
     * [text] moved one step in [direction] (negative is down) and clamped into
     * [spec]'s bounds. A field that is empty or not a number starts at
     * [PlanStepperSpec.startWhenEmpty] instead - the stepper replaces it with a
     * real value rather than refusing to move.
     */
    fun stepped(
        text: String,
        direction: Int,
        spec: PlanStepperSpec,
    ): String {
        val current = text.trim().toIntOrNull() ?: return spec.startWhenEmpty.toString()
        val moved = if (direction > 0) current + spec.step else current - spec.step
        return moved.coerceIn(spec.min, spec.max).toString()
    }

    /** A keypad entry, rounded to a whole number and clamped into [spec]'s bounds. */
    fun typed(
        entered: BigDecimal,
        spec: PlanStepperSpec,
    ): String =
        entered
            .setScale(0, RoundingMode.HALF_UP)
            .coerceIn(BigDecimal(spec.min), BigDecimal(spec.max))
            .toInt()
            .toString()
}

// The design's preset values, transcribed (as the exercise editor's own presets are).
@Suppress("MagicNumber")
private val REST_PRESET_SECONDS = listOf(60, 75, 90, 120, 150, 180)

/** `4a`'s day count (`:3657`): the planned working sets across every row; a row still being typed counts as zero. */
internal fun plannedWorkingSetTotal(rows: List<PlannedExerciseRowUiState>): Int = rows.sumOf { it.targetSetsText.trim().toIntOrNull() ?: 0 }
