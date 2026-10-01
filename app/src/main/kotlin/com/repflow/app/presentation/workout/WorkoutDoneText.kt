package com.repflow.app.presentation.workout

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import com.repflow.app.application.history.BestSet
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import java.math.BigDecimal

/*
 * The done screen's words (remediation-1 CP9): a recap row's detail and
 * delta, and a best set, from the plain values in `WorkoutDoneModel.kt`.
 */

/** `3 sets · 80 kg × 8, 8; 82.5 kg × 6`, `2 warm-ups only`, or `no sets recorded`. */
@Composable
internal fun recapDetail(row: RecapRowUi): String =
    when {
        row.workingSets > 0 -> {
            pluralStringResource(
                R.plurals.workout_done_recap_detail,
                row.workingSets,
                row.workingSets,
                row.groups.map { groupText(it) }.joinToString(separator = "; "),
            )
        }

        row.warmups > 0 -> {
            pluralStringResource(R.plurals.workout_done_recap_warmups_only, row.warmups, row.warmups)
        }

        else -> {
            stringResource(R.string.workout_done_recap_no_sets)
        }
    }

@Composable
private fun groupText(group: RecapGroup): String =
    when (group) {
        is RecapGroup.Loaded -> stringResource(R.string.workout_done_recap_load_group, kgText(group.kg), group.reps.joinToString())
        is RecapGroup.Unloaded -> stringResource(R.string.workout_done_recap_reps_group, group.reps.joinToString())
        is RecapGroup.Timed -> stringResource(R.string.workout_done_recap_seconds_group, group.seconds.joinToString())
    }

/** `+2.5 kg`, `−1 rep`, `same time`, `first time`, or `—`. */
@Composable
internal fun deltaText(delta: RecapDelta): String =
    when (delta) {
        RecapDelta.NoWorkingSets -> stringResource(R.string.workout_done_delta_none)
        RecapDelta.FirstTime -> stringResource(R.string.workout_done_delta_first)
        is RecapDelta.Load -> loadDeltaText(delta.kg)
        is RecapDelta.Reps -> repsDeltaText(delta.reps)
        is RecapDelta.Seconds -> secondsDeltaText(delta.seconds)
    }

@Composable
private fun loadDeltaText(kg: BigDecimal): String =
    when (kg.signum()) {
        1 -> stringResource(R.string.workout_done_delta_load_up, kgText(kg))
        -1 -> stringResource(R.string.workout_done_delta_load_down, kgText(kg.negate()))
        else -> stringResource(R.string.workout_done_delta_load_same)
    }

@Composable
private fun repsDeltaText(reps: Int): String =
    when {
        reps > 0 -> pluralStringResource(R.plurals.workout_done_delta_reps_up, reps, reps)
        reps < 0 -> pluralStringResource(R.plurals.workout_done_delta_reps_down, -reps, -reps)
        else -> stringResource(R.string.workout_done_delta_reps_same)
    }

@Composable
private fun secondsDeltaText(seconds: Int): String =
    when {
        seconds > 0 -> stringResource(R.string.workout_done_delta_seconds_up, seconds)
        seconds < 0 -> stringResource(R.string.workout_done_delta_seconds_down, -seconds)
        else -> stringResource(R.string.workout_done_delta_seconds_same)
    }

/** `82.5 kg × 7`, `12 reps`, `45 s`. */
@Composable
internal fun bestSetText(set: BestSet): String =
    when (set) {
        is BestSet.Load -> stringResource(R.string.workout_done_set_load, kgText(BigDecimal.valueOf(set.kg)), set.reps)
        is BestSet.Reps -> pluralStringResource(R.plurals.workout_done_set_reps, set.reps, set.reps)
        is BestSet.Seconds -> stringResource(R.string.workout_done_set_seconds, set.seconds)
    }

/** An improvement is drawn in the accent; anything else in the secondary text colour. */
internal fun RecapDelta.isGain(): Boolean =
    when (this) {
        is RecapDelta.Load -> kg.signum() > 0
        is RecapDelta.Reps -> reps > 0
        is RecapDelta.Seconds -> seconds > 0
        RecapDelta.FirstTime, RecapDelta.NoWorkingSets -> false
    }

/** `82.5`, `80` - the stepper's own load format. */
private fun kgText(kg: BigDecimal): String = RepFlowStepperMath.format(kg)
