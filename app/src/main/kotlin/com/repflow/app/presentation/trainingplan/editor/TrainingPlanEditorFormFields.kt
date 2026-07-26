package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError

/**
 * Form field composables for [TrainingPlanEditorScreen], split out purely to
 * keep each file under Detekt's per-file function-count threshold (see
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorFormFields]).
 */
@Composable
internal fun EditorForm(
    uiState: TrainingPlanEditorUiState,
    modifier: Modifier,
    onNameChanged: (String) -> Unit,
    rowActions: TrainingPlanEditorRowActions,
    onAddRowClicked: () -> Unit,
    onSaveClicked: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        NameField(uiState, onNameChanged)
        Spacer(Modifier.padding(top = 8.dp))
        RowsSection(uiState, rowActions, onAddRowClicked)
        Spacer(Modifier.padding(top = 8.dp))
        SubmitErrorText(uiState.submitError)
        Button(onClick = onSaveClicked, enabled = uiState.isSaveEnabled, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (uiState.isSaving) {
                    stringResource(R.string.training_plan_editor_saving)
                } else {
                    stringResource(R.string.training_plan_editor_save)
                },
            )
        }
    }
}

@Composable
private fun NameField(
    uiState: TrainingPlanEditorUiState,
    onNameChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.training_plan_editor_name_label)) },
        isError = uiState.nameError != null,
        supportingText = { fieldErrorText(uiState.nameError)?.let { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RowsSection(
    uiState: TrainingPlanEditorUiState,
    rowActions: TrainingPlanEditorRowActions,
    onAddRowClicked: () -> Unit,
) {
    Column {
        if (uiState.rows.isEmpty()) {
            Text(stringResource(R.string.training_plan_editor_no_exercises))
        }
        uiState.rows.forEachIndexed { index, row ->
            PlannedExerciseRow(
                row = row,
                availableExercises = uiState.availableExercises,
                canMoveUp = index > 0,
                canMoveDown = index < uiState.rows.lastIndex,
                actions = rowActions,
            )
            Spacer(Modifier.padding(top = 8.dp))
        }
        OutlinedButton(onClick = onAddRowClicked, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.training_plan_editor_add_exercise))
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PlannedExerciseRow(
    row: PlannedExerciseRowUiState,
    availableExercises: List<TrainingPlanEditorExerciseOption>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: TrainingPlanEditorRowActions,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                ExercisePicker(row, availableExercises) { exerciseId -> actions.onExerciseSelected(row.rowId, exerciseId) }
                RowMoveAndRemoveActions(row.rowId, canMoveUp, canMoveDown, actions)
            }
            OutlinedTextField(
                value = row.targetSetsText,
                onValueChange = { actions.onTargetSetsChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_target_sets_label)) },
                isError = row.targetSetsError != null,
                supportingText = { fieldErrorText(row.targetSetsError)?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TargetRangeFields(row, actions)
            OutlinedTextField(
                value = row.restSecondsText,
                onValueChange = { actions.onRestSecondsChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_rest_label)) },
                isError = row.restError != null,
                supportingText = { fieldErrorText(row.restError)?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Checkbox(checked = row.isOptional, onCheckedChange = { actions.onOptionalChanged(row.rowId, it) })
                Text(stringResource(R.string.training_plan_editor_row_optional_label))
            }
        }
    }
}

@Composable
private fun TargetRangeFields(
    row: PlannedExerciseRowUiState,
    actions: TrainingPlanEditorRowActions,
) {
    if (row.trackingType == ExerciseTrackingType.DURATION) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = row.durationMinText,
                onValueChange = { actions.onDurationMinChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_duration_min_label)) },
                isError = row.targetRangeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.durationMaxText,
                onValueChange = { actions.onDurationMaxChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_duration_max_label)) },
                isError = row.targetRangeError != null,
                supportingText = { fieldErrorText(row.targetRangeError)?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = row.repMinText,
                onValueChange = { actions.onRepMinChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_rep_min_label)) },
                isError = row.targetRangeError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.repMaxText,
                onValueChange = { actions.onRepMaxChanged(row.rowId, it) },
                label = { Text(stringResource(R.string.training_plan_editor_row_rep_max_label)) },
                isError = row.targetRangeError != null,
                supportingText = { fieldErrorText(row.targetRangeError)?.let { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ExercisePicker(
    row: PlannedExerciseRowUiState,
    availableExercises: List<TrainingPlanEditorExerciseOption>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                row.exerciseName.ifBlank { stringResource(R.string.training_plan_editor_select_exercise_placeholder) },
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            availableExercises.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RowMoveAndRemoveActions(
    rowId: Long,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: TrainingPlanEditorRowActions,
) {
    val moveUpDescription = stringResource(R.string.training_plan_editor_row_move_up_content_description)
    val moveDownDescription = stringResource(R.string.training_plan_editor_row_move_down_content_description)
    val removeDescription = stringResource(R.string.training_plan_editor_row_remove_content_description)
    Row {
        TextButton(
            onClick = { actions.onMoveUp(rowId) },
            enabled = canMoveUp,
            modifier = Modifier.semantics { contentDescription = moveUpDescription },
        ) { Text("↑") }
        TextButton(
            onClick = { actions.onMoveDown(rowId) },
            enabled = canMoveDown,
            modifier = Modifier.semantics { contentDescription = moveDownDescription },
        ) { Text("↓") }
        TextButton(
            onClick = { actions.onRemove(rowId) },
            modifier = Modifier.semantics { contentDescription = removeDescription },
        ) { Text("✕") }
    }
}

@Composable
private fun SubmitErrorText(submitError: TrainingPlanEditorSubmitError?) {
    val text =
        when (submitError?.kind) {
            TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME -> stringResource(R.string.training_plan_editor_submit_error_duplicate_name)
            TrainingPlanEditorSubmitErrorKind.INVALID -> stringResource(R.string.training_plan_editor_submit_error_invalid)
            TrainingPlanEditorSubmitErrorKind.UNAVAILABLE -> stringResource(R.string.training_plan_editor_submit_error_unavailable)
            null -> null
        }
    text?.let {
        Text(it)
        Spacer(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun fieldErrorText(error: TrainingPlanEditorFieldError?): String? =
    when (error) {
        is TrainingPlanEditorFieldError.Domain -> domainErrorText(error.error)
        TrainingPlanEditorFieldError.InvalidNumber -> stringResource(R.string.training_plan_editor_error_invalid_number)
        TrainingPlanEditorFieldError.Required -> stringResource(R.string.training_plan_editor_error_no_exercises)
        null -> null
    }

@Composable
private fun domainErrorText(error: TrainingPlanValidationError): String =
    when (error) {
        TrainingPlanValidationError.NameBlank -> stringResource(R.string.training_plan_editor_error_name_blank)
        TrainingPlanValidationError.NameTooLong -> stringResource(R.string.training_plan_editor_error_name_too_long)
        else -> stringResource(R.string.training_plan_editor_error_invalid_number)
    }
