package com.repflow.app.presentation.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId

/**
 * Extracted from `ActiveWorkoutScreen.kt` (Milestone 8, implementation-review
 * findings #2/#3) to stay under detekt's per-file `TooManyFunctions`
 * threshold once the planned-target progress display and tracking-type-aware
 * field rendering landed - a cohesive unit (the per-exercise card and its
 * helpers), not an arbitrary split.
 */
@Composable
internal fun ExerciseCard(
    exercise: ActiveExerciseUi,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
) {
    var loadText by remember(exercise.id) { mutableStateOf("") }
    var repsText by remember(exercise.id) { mutableStateOf("") }
    var durationText by remember(exercise.id) { mutableStateOf("") }
    var rpeText by remember(exercise.id) { mutableStateOf("") }
    var isWarmup by remember(exercise.id) { mutableStateOf(false) }
    var painText by remember(exercise.id) { mutableStateOf("") }
    var techniqueQualityText by remember(exercise.id) { mutableStateOf("") }

    fun clearEntryFields() {
        loadText = ""
        repsText = ""
        durationText = ""
        rpeText = ""
        isWarmup = false
        painText = ""
        techniqueQualityText = ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(exercise.name)
        exercise.plannedTarget?.let { target -> PlannedTargetSummary(target, exercise.sets) }
        for ((set, isExtra) in exercise.setsWithExtraFlag()) {
            SetSummaryRow(set, exercise.trackingType, isExtra)
        }
        // Only the field(s) the exercise's tracking type actually persists are shown -
        // WorkoutSet.create rejects reps/load for a DURATION exercise and rejects
        // durationSeconds for the other two (implementation-review finding #3).
        when (exercise.trackingType) {
            ExerciseTrackingType.WEIGHT_AND_REPS -> {
                Row {
                    OutlinedTextField(
                        value = loadText,
                        onValueChange = { loadText = it },
                        label = { Text(stringResource(R.string.workout_active_load_label)) },
                        keyboardOptions =
                            androidx.compose.foundation.text
                                .KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = { Text(stringResource(R.string.workout_active_reps_label)) },
                        keyboardOptions =
                            androidx.compose.foundation.text
                                .KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            ExerciseTrackingType.REPS_ONLY -> {
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text(stringResource(R.string.workout_active_reps_label)) },
                    keyboardOptions =
                        androidx.compose.foundation.text
                            .KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ExerciseTrackingType.DURATION -> {
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text(stringResource(R.string.workout_active_duration_label)) },
                    keyboardOptions =
                        androidx.compose.foundation.text
                            .KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row {
            OutlinedTextField(
                value = rpeText,
                onValueChange = { rpeText = it },
                label = { Text(stringResource(R.string.workout_active_rpe_label)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.workout_active_warmup_label), modifier = Modifier.weight(1f))
            Switch(checked = isWarmup, onCheckedChange = { isWarmup = it })
        }
        Row {
            OutlinedTextField(
                value = painText,
                onValueChange = { painText = it },
                label = { Text(stringResource(R.string.workout_active_pain_label)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = techniqueQualityText,
                onValueChange = { techniqueQualityText = it },
                label = { Text(stringResource(R.string.workout_active_technique_quality_label)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        Row {
            Button(
                onClick = {
                    onRecordSet(
                        exercise.id,
                        loadText.toDoubleOrNull(),
                        repsText.toIntOrNull(),
                        durationText.toIntOrNull(),
                        rpeText.toDoubleOrNull(),
                        isWarmup,
                        painText.toIntOrNull(),
                        techniqueQualityText.toIntOrNull(),
                    )
                    // Cleared immediately (Milestone 8, implementation-review finding #3):
                    // a deliberate choice, not an oversight - each entry row starts fresh
                    // for the next set rather than risking an accidental duplicate submit.
                    clearEntryFields()
                },
            ) {
                Text(stringResource(R.string.workout_active_add_set))
            }
            if (exercise.sets.isNotEmpty()) {
                TextButton(onClick = { onUndoLastSet(exercise.id) }) {
                    Text(stringResource(R.string.workout_active_undo_set))
                }
                TextButton(
                    onClick = {
                        onEditLastSet(
                            exercise.id,
                            loadText.toDoubleOrNull(),
                            repsText.toIntOrNull(),
                            durationText.toIntOrNull(),
                            rpeText.toDoubleOrNull(),
                            isWarmup,
                            painText.toIntOrNull(),
                            techniqueQualityText.toIntOrNull(),
                        )
                        clearEntryFields()
                    },
                ) {
                    Text(stringResource(R.string.workout_active_edit_set))
                }
            }
        }
    }
}

/** Warm-up/working-set progress, target rep/duration range, and planned rest - resolved from the plan this exercise was seeded from (Milestone 8, implementation-review finding #2). */
@Composable
private fun PlannedTargetSummary(
    target: PlannedTargetUi,
    sets: List<ActiveSetUi>,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        target.targetWarmupSets?.let { targetWarmup ->
            Text(
                stringResource(
                    R.string.workout_active_plan_warmup_progress,
                    sets.count { it.isWarmup },
                    targetWarmup,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            stringResource(
                R.string.workout_active_plan_working_progress,
                sets.count { !it.isWarmup },
                target.targetWorkingSets,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        target.repRange?.let { range ->
            Text(
                stringResource(R.string.workout_active_plan_rep_range, range.first, range.last),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        target.durationRangeSeconds?.let { range ->
            Text(
                stringResource(R.string.workout_active_plan_duration_range, range.first, range.last),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        target.restSeconds?.let { rest ->
            Text(stringResource(R.string.workout_active_plan_rest, rest), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Every field shown here (Milestone 8, implementation-review finding #3) is
 * one [com.repflow.app.domain.workout.WorkoutSet.create] actually accepts
 * for a set of [trackingType] - no `?: 0`/`?: 0.0` standing in for a field
 * that's genuinely absent (e.g. an untracked `load` on a bodyweight
 * `WEIGHT_AND_REPS` set falls back to the reps-only row instead of
 * claiming "0.0 kg").
 */
@Composable
private fun SetSummaryRow(
    set: ActiveSetUi,
    trackingType: ExerciseTrackingType,
    isExtra: Boolean,
) {
    Column(modifier = Modifier.padding(bottom = 4.dp)) {
        val warmupSuffix = if (set.isWarmup) " " + stringResource(R.string.workout_active_warmup_suffix) else ""
        val extraSuffix = if (isExtra) " " + stringResource(R.string.workout_active_set_extra_suffix) else ""
        val primaryText =
            when {
                trackingType == ExerciseTrackingType.DURATION -> {
                    stringResource(R.string.workout_active_set_row_duration, set.setNumber, set.durationSeconds ?: 0)
                }

                trackingType == ExerciseTrackingType.WEIGHT_AND_REPS && set.load != null -> {
                    stringResource(R.string.workout_active_set_row_weight_reps, set.setNumber, set.load, set.reps ?: 0)
                }

                else -> {
                    stringResource(R.string.workout_active_set_row_reps, set.setNumber, set.reps ?: 0)
                }
            }
        Text(primaryText + warmupSuffix + extraSuffix)
        set.rpe?.let { rpe ->
            Text(stringResource(R.string.workout_active_set_rpe, rpe.toString()), style = MaterialTheme.typography.bodySmall)
        }
        set.pain?.let { pain -> Text(stringResource(R.string.workout_active_set_pain, pain), style = MaterialTheme.typography.bodySmall) }
        set.techniqueQuality?.let { quality ->
            Text(stringResource(R.string.workout_active_set_technique_quality, quality), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Pairs each set with whether it falls beyond its planned warm-up/working
 * quota (Milestone 8, implementation-review finding #2) - `false` for every
 * set on an ad-hoc exercise ([ActiveExerciseUi.plannedTarget] is `null`).
 */
private fun ActiveExerciseUi.setsWithExtraFlag(): List<Pair<ActiveSetUi, Boolean>> {
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
