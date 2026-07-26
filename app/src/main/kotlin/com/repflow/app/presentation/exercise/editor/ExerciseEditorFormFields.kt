package com.repflow.app.presentation.exercise.editor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType

/**
 * Form field composables for [ExerciseEditorScreen], split into their own
 * file purely to keep each file under Detekt's per-file function-count
 * threshold - all of this still belongs to the same stateless screen (see
 * plan.md section H).
 *
 * The preset values below are the UI's actual product content (plan.md
 * section B), not arbitrary magic numbers - a named constant per value would
 * only rename this same list.
 */
@Suppress("MagicNumber")
private val REST_SECONDS_PRESETS = listOf(60L, 90L, 120L, 180L)
private val LOAD_INCREMENT_KG_PRESETS = listOf("1.25", "2.5", "5")

@Suppress("LongParameterList")
@Composable
internal fun EditorForm(
    uiState: ExerciseEditorUiState,
    modifier: Modifier,
    onNameChanged: (String) -> Unit,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onRestSecondsChanged: (String) -> Unit,
    onLoadIncrementChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        NameField(uiState, onNameChanged)
        Spacer16()
        TrackingTypeField(uiState.trackingType, onTrackingTypeChanged)
        Spacer16()
        InstructionsField(uiState, onInstructionsChanged)
        Spacer16()
        RestDurationField(uiState, onRestSecondsChanged)
        Spacer16()
        if (uiState.trackingType.supportsLoad) {
            LoadIncrementField(uiState, onLoadIncrementChanged)
            Spacer16()
        }
        SubmitErrorText(uiState.submitError)
        Button(
            onClick = onSaveClicked,
            enabled = uiState.isSaveEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (uiState.isSaving) {
                    stringResource(R.string.exercise_editor_saving)
                } else {
                    stringResource(R.string.exercise_editor_save)
                },
            )
        }
    }
}

@Composable
private fun Spacer16() = Spacer(Modifier.padding(top = 8.dp))

@Composable
private fun NameField(
    uiState: ExerciseEditorUiState,
    onNameChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.exercise_editor_name_label)) },
        isError = uiState.nameError != null,
        supportingText = { fieldErrorText(uiState.nameError)?.let { Text(it) } },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrackingTypeField(
    trackingType: ExerciseTrackingType,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
) {
    Column {
        Text(stringResource(R.string.exercise_editor_tracking_type_label))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseTrackingType.entries.forEach { type ->
                FilterChip(
                    selected = trackingType == type,
                    onClick = { onTrackingTypeChanged(type) },
                    label = { Text(trackingTypeLabel(type)) },
                )
            }
        }
    }
}

@Composable
private fun trackingTypeLabel(trackingType: ExerciseTrackingType): String =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.exercise_tracking_type_weight_and_reps)
        ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.exercise_tracking_type_reps_only)
        ExerciseTrackingType.DURATION -> stringResource(R.string.exercise_tracking_type_duration)
    }

@Composable
private fun InstructionsField(
    uiState: ExerciseEditorUiState,
    onInstructionsChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = uiState.instructions,
        onValueChange = onInstructionsChanged,
        label = { Text(stringResource(R.string.exercise_editor_instructions_label)) },
        isError = uiState.instructionsError != null,
        supportingText = { fieldErrorText(uiState.instructionsError)?.let { Text(it) } },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RestDurationField(
    uiState: ExerciseEditorUiState,
    onRestSecondsChanged: (String) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            REST_SECONDS_PRESETS.forEach { preset ->
                OutlinedButton(onClick = { onRestSecondsChanged(preset.toString()) }) {
                    Text(preset.toString())
                }
            }
        }
        OutlinedTextField(
            value = uiState.restSecondsText,
            onValueChange = onRestSecondsChanged,
            label = { Text(stringResource(R.string.exercise_editor_rest_duration_label)) },
            isError = uiState.restDurationError != null,
            supportingText = { fieldErrorText(uiState.restDurationError)?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LoadIncrementField(
    uiState: ExerciseEditorUiState,
    onLoadIncrementChanged: (String) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOAD_INCREMENT_KG_PRESETS.forEach { preset ->
                OutlinedButton(onClick = { onLoadIncrementChanged(preset) }) {
                    Text(preset)
                }
            }
        }
        OutlinedTextField(
            value = uiState.loadIncrementKgText,
            onValueChange = onLoadIncrementChanged,
            label = { Text(stringResource(R.string.exercise_editor_load_increment_label)) },
            isError = uiState.loadIncrementError != null,
            supportingText = { fieldErrorText(uiState.loadIncrementError)?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
