package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowNumericKeypad
import com.repflow.app.presentation.designsystem.components.RepFlowPillPicker
import com.repflow.app.presentation.designsystem.components.RepFlowPillPickerDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowStepper
import com.repflow.app.presentation.designsystem.components.RepFlowStepperSizing
import com.repflow.app.presentation.exercise.editor.InlineError

/**
 * An open row's targets (remediation-1 CP11): `4a`'s stepper lines
 * (`RepFlow.dc.html:919-935`) - label left, 44 −/+ either side of a 16
 * tabular value - for working sets, `2a`'s warm-up sets, the rep (or duration)
 * minimum and maximum (`2a`'s two steppers, `:2295-2318`, rather than `4a`'s
 * single range stepper, so either end can move alone), and rest; `2a`'s rest
 * presets under the rest line; then `Optional` and `Change exercise`.
 *
 * Every stepper is CP3's: the value is a button that opens the keypad. Each
 * writes the row's text field through the ViewModel's existing setter, so
 * validation, errors and save are untouched; a field's error shows under its
 * own line.
 */
@Composable
internal fun PlannedExerciseTargets(
    row: PlannedExerciseRowUiState,
    actions: TrainingPlanEditorRowActions,
    onChangeExerciseClick: () -> Unit,
) {
    val id = row.rowId
    Column(modifier = Modifier.fillMaxWidth().padding(start = TargetsStartPadding, bottom = TargetsBottomPadding)) {
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_target_sets_label),
            text = row.targetSetsText,
            spec = PlanRowStepping.workingSets,
            onValueChange = { actions.onTargetSetsChanged(id, it) },
            descriptions = R.string.training_plan_editor_sets_decrease to R.string.training_plan_editor_sets_increase,
        )
        LineError(fieldErrorText(row.targetSetsError))
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_target_warmup_sets_label),
            text = row.targetWarmupSetsText,
            spec = PlanRowStepping.warmupSets,
            onValueChange = { actions.onTargetWarmupSetsChanged(id, it) },
            descriptions = R.string.training_plan_editor_warmup_decrease to R.string.training_plan_editor_warmup_increase,
        )
        LineError(fieldErrorText(row.targetWarmupSetsError))
        RangeLines(row, actions)
        LineError(fieldErrorText(row.targetRangeError))
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_rest_label),
            text = row.restSecondsText,
            spec = PlanRowStepping.rest,
            onValueChange = { actions.onRestSecondsChanged(id, it) },
            descriptions = R.string.training_plan_editor_rest_decrease to R.string.training_plan_editor_rest_increase,
            format = { restClock(it) },
        )
        RestPresets(restSecondsText = row.restSecondsText, onRestSecondsChanged = { actions.onRestSecondsChanged(id, it) })
        LineError(fieldErrorText(row.restError))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapMd),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            OptionalToggle(
                checked = row.isOptional,
                onCheckedChange = { actions.onOptionalChanged(id, it) },
                modifier = Modifier.weight(1f),
            )
            ChangeExerciseButton(onClick = onChangeExerciseClick, modifier = Modifier.weight(1f))
        }
    }
}

/** The rep range (`Min reps` / `Max reps`), or `Min seconds` / `Max seconds` for a duration exercise. */
@Composable
private fun RangeLines(
    row: PlannedExerciseRowUiState,
    actions: TrainingPlanEditorRowActions,
) {
    val id = row.rowId
    val minDescriptions = R.string.training_plan_editor_minimum_decrease to R.string.training_plan_editor_minimum_increase
    val maxDescriptions = R.string.training_plan_editor_maximum_decrease to R.string.training_plan_editor_maximum_increase
    if (row.trackingType == ExerciseTrackingType.DURATION) {
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_duration_min_label),
            text = row.durationMinText,
            spec = PlanRowStepping.durationMin,
            onValueChange = { actions.onDurationMinChanged(id, it) },
            descriptions = minDescriptions,
        )
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_duration_max_label),
            text = row.durationMaxText,
            spec = PlanRowStepping.durationMax,
            onValueChange = { actions.onDurationMaxChanged(id, it) },
            descriptions = maxDescriptions,
        )
    } else {
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_rep_min_label),
            text = row.repMinText,
            spec = PlanRowStepping.repMin,
            onValueChange = { actions.onRepMinChanged(id, it) },
            descriptions = minDescriptions,
        )
        StepperLine(
            label = stringResource(R.string.training_plan_editor_row_rep_max_label),
            text = row.repMaxText,
            spec = PlanRowStepping.repMax,
            onValueChange = { actions.onRepMaxChanged(id, it) },
            descriptions = maxDescriptions,
        )
    }
}

/**
 * One `4a` stepper line: the label at 13.5, then CP3's stepper at `4a`'s
 * in-line size (44 buttons, value 16 tabular). An empty field shows `–`; its
 * first step lands on [PlanStepperSpec.startWhenEmpty]. The value opens the
 * keypad, titled with the line's label.
 */
@Composable
private fun StepperLine(
    label: String,
    text: String,
    spec: PlanStepperSpec,
    onValueChange: (String) -> Unit,
    descriptions: Pair<Int, Int>,
    format: @Composable (Int) -> String = { it.toString() },
) {
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    val value = text.trim().toIntOrNull()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LineVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = LineLabelFontSize),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = LINE_LABEL_ALPHA),
            modifier = Modifier.weight(1f),
        )
        RepFlowStepper(
            value = value?.let { format(it) } ?: text.trim().ifEmpty { EMPTY_VALUE },
            onDecrement = { onValueChange(PlanRowStepping.stepped(text, -1, spec)) },
            onIncrement = { onValueChange(PlanRowStepping.stepped(text, 1, spec)) },
            decrementContentDescription = stringResource(descriptions.first),
            incrementContentDescription = stringResource(descriptions.second),
            sizing = LineStepperSizing,
            onValueClick = { keypadOpen = true },
            modifier = Modifier.width(LineStepperWidth),
        )
    }
    if (keypadOpen) {
        RepFlowNumericKeypad(
            title = label,
            allowDecimal = false,
            onDismissRequest = { keypadOpen = false },
            onConfirm = { entered ->
                keypadOpen = false
                onValueChange(PlanRowStepping.typed(entered, spec))
            },
        )
    }
}

/**
 * `2a`'s rest presets (`:2321-2325`): six cells on `6b`'s scale-row sizing,
 * the current one tinted. A preset writes its seconds through the same setter
 * the stepper does; tapping the selected preset again clears the optional
 * rest, as the exercise editor's presets do (CP10).
 */
@Composable
private fun RestPresets(
    restSecondsText: String,
    onRestSecondsChanged: (String) -> Unit,
) {
    val presets = PlanRowStepping.restPresetSeconds
    val selected = presets.indexOf(restSecondsText.trim().toIntOrNull()).takeIf { it >= 0 }
    RepFlowPillPicker(
        options = presets.map { restClock(it) },
        selectedIndex = selected,
        onSelect = { index -> onRestSecondsChanged(if (index == selected) "" else presets[index].toString()) },
        sizing = RepFlowPillPickerDefaults.scale,
        modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapXs),
    )
}

@Composable
private fun restClock(seconds: Int): String =
    stringResource(
        R.string.exercise_rest_clock,
        seconds / SECONDS_PER_MINUTE,
        seconds % SECONDS_PER_MINUTE,
    )

@Composable
private fun LineError(text: String?) {
    if (text != null) {
        Box(modifier = Modifier.padding(bottom = RepFlowSpacing.gapXs)) {
            InlineError(text)
        }
    }
}

private const val EMPTY_VALUE = "–"
private const val SECONDS_PER_MINUTE = 60
private const val LINE_LABEL_ALPHA = 0.75f

/** `4a`'s in-line stepper: 44 buttons at radius 9, the value at 16 tabular. */
private val LineStepperSizing =
    RepFlowStepperSizing(
        buttonSize = 44.dp,
        buttonShape = RepFlowShapes.stepper,
        valueFontSize = 16.sp,
        valueLineHeight = 22.sp,
    )
private val LineStepperWidth = 164.dp
private val LineVerticalPadding = 7.dp
private val LineLabelFontSize = 13.5.sp
private val TargetsStartPadding = 6.dp
private val TargetsBottomPadding = 14.dp
