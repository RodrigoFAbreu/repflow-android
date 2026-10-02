package com.repflow.app.presentation.exercise.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowPillPicker
import com.repflow.app.presentation.designsystem.components.RepFlowPillPickerDefaults
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.exercise.ExerciseUiFormatting

/**
 * Form field composables for [ExerciseEditorScreen], split into their own
 * file purely to keep each file under Detekt's per-file function-count
 * threshold - all of this still belongs to the same stateless screen (see
 * plan.md section H).
 *
 * `2b` (`RepFlow.dc.html:2373-2416`, remediation-1 CP10): the name field with
 * its inline error, `Tracking type` as a three-segment control whose selected
 * segment carries a check, `Default rest` and `Load step` as preset rows with
 * `Other` for a typed value, and the technique notes. Presets feed the same
 * `String` setters a typed value does.
 *
 * The preset values below are the UI's actual product content (plan.md
 * section B, `2b`), not arbitrary magic numbers - a named constant per value
 * would only rename this same list.
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
    onNameFocusLost: () -> Unit,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onRestSecondsChanged: (String) -> Unit,
    onLoadIncrementChanged: (String) -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = RepFlowSpacing.gapSm, bottom = RepFlowSpacing.screenPadding),
    ) {
        NameField(uiState, onNameChanged, onNameFocusLost)
        FieldLabel(stringResource(R.string.exercise_editor_tracking_type_label))
        TrackingTypeSegments(uiState.trackingType, onTrackingTypeChanged)
        FieldLabel(stringResource(R.string.exercise_editor_default_rest_label))
        RestDurationPresets(uiState, onRestSecondsChanged)
        if (uiState.trackingType.supportsLoad) {
            FieldLabel(stringResource(R.string.exercise_editor_load_step_label))
            LoadStepPresets(uiState, onLoadIncrementChanged)
        }
        InstructionsField(uiState, onInstructionsChanged)
        if (uiState.submitError?.kind == ExerciseEditorSubmitErrorKind.UNAVAILABLE) {
            Box(modifier = Modifier.padding(top = SectionGap)) { SubmitErrorText(uiState.submitError) }
        }
    }
}

/** `2b`'s field label: 11.5 at the secondary tier, 18 above its control and 6 below. */
@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = FieldLabelFontSize),
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        modifier = Modifier.padding(top = SectionGap, bottom = RepFlowSpacing.gapXs),
    )
}

/**
 * The name, with `2b`'s inline error (`:2383-2385`): a `warning-circle` and
 * the words under the field, which takes the error ring. A save refused for a
 * duplicate name reports here, against the field it is about, rather than
 * beside the save action.
 */
@Composable
private fun NameField(
    uiState: ExerciseEditorUiState,
    onNameChanged: (String) -> Unit,
    onNameFocusLost: () -> Unit,
) {
    val duplicateName = uiState.submitError?.kind == ExerciseEditorSubmitErrorKind.DUPLICATE_NAME && !uiState.isSaving
    // Read unconditionally: a composable call that comes and goes with the error shifts the
    // field's slot and drops its focus and the keyboard (functional review R3-F-4).
    val duplicateNameText = stringResource(R.string.exercise_editor_submit_error_duplicate_name)
    val error = fieldErrorText(uiState.visibleNameError) ?: duplicateNameText.takeIf { duplicateName }
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.exercise_editor_name_label)) },
        isError = error != null,
        supportingText = error?.let { { InlineError(it) } },
        singleLine = true,
        shape = FieldShape,
        colors = editorFieldColors(),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = NameFieldMinHeight)
                .notifyOnFocusLost(onNameFocusLost)
                .bringIntoViewWhen(duplicateName, uiState.submitError),
    )
}

/**
 * `2b`'s tracking type (`:2387-2392`): one 46-tall control in a hairline ring,
 * three equal segments; the selected one is accent-tinted **and** carries a
 * `check-fat`, so the choice is never shown by colour alone.
 */
@Composable
private fun TrackingTypeSegments(
    trackingType: ExerciseTrackingType,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
) {
    val hairline = RepFlowColor.hairline
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SegmentMinHeight)
                .clip(FieldShape)
                .border(1.dp, hairline, FieldShape)
                .selectableGroup(),
    ) {
        ExerciseTrackingType.entries.forEachIndexed { index, type ->
            if (index > 0) {
                Box(modifier = Modifier.size(width = 1.dp, height = SegmentMinHeight).background(hairline))
            }
            TrackingTypeSegment(
                label = trackingTypeLabel(type),
                selected = type == trackingType,
                onClick = { onTrackingTypeChanged(type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrackingTypeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    Row(
        modifier =
            modifier
                .heightIn(min = SegmentMinHeight)
                .background(if (selected) selectedColors.fill else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(RepFlowIcons.checkFat),
                contentDescription = null,
                tint = selectedColors.label,
                modifier = Modifier.size(SegmentCheckSize),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = SegmentFontSize),
            color = if (selected) selectedColors.label else repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
    }
}

@Composable
private fun trackingTypeLabel(trackingType: ExerciseTrackingType): String =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.exercise_tracking_type_weight_and_reps)
        ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.exercise_tracking_type_reps_only)
        ExerciseTrackingType.DURATION -> stringResource(R.string.exercise_tracking_type_duration)
    }

/**
 * `2b`'s `Default rest` presets (`:2394-2401`): `1:00 1:30 2:00 3:00 Other`,
 * on `6b`'s scale-row cells. A preset writes its seconds through the same
 * setter typing does; tapping the selected preset again clears the optional
 * default. `Other` opens the typed field, and stays selected for any value
 * that is not a preset (so a restored or invalid draft is never hidden).
 * Once chosen, `Other` stays open until a preset is tapped - even while the
 * typed text passes through a preset's value (`60` on the way to `600`), so
 * typing never closes the field under the user's fingers. While it is open, a
 * preset tap selects that preset; only tapping the preset already shown as
 * selected clears the default.
 */
@Composable
private fun RestDurationPresets(
    uiState: ExerciseEditorUiState,
    onRestSecondsChanged: (String) -> Unit,
) {
    val presetIndex = REST_SECONDS_PRESETS.indexOfFirst { it.toString() == uiState.restSecondsText.trim() }
    var otherChosen by rememberSaveable { mutableStateOf(false) }
    val showOther = otherChosen || (presetIndex < 0 && uiState.restSecondsText.isNotBlank())
    val labels =
        REST_SECONDS_PRESETS.map { seconds ->
            stringResource(R.string.exercise_rest_clock, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
        } + stringResource(R.string.exercise_editor_preset_other)
    RepFlowPillPicker(
        options = labels,
        selectedIndex = if (showOther) REST_SECONDS_PRESETS.size else presetIndex.takeIf { it >= 0 },
        onSelect = { index ->
            if (index == REST_SECONDS_PRESETS.size) {
                otherChosen = true
            } else {
                otherChosen = false
                val preset = REST_SECONDS_PRESETS[index].toString()
                onRestSecondsChanged(if (!showOther && index == presetIndex) "" else preset)
            }
        },
        sizing = RepFlowPillPickerDefaults.scale,
    )
    if (showOther) {
        OtherValueField(
            value = uiState.restSecondsText,
            onValueChange = onRestSecondsChanged,
            label = stringResource(R.string.exercise_editor_rest_duration_label),
            error = fieldErrorText(uiState.restDurationError),
            keyboardType = KeyboardType.Number,
        )
    }
}

/**
 * `2b`'s `Load step` presets (`:2403-2409`): `1.25 kg 2.5 kg 5 kg Other`,
 * the same rules as the rest presets. Shown only for a tracking type that
 * carries load, as the typed field was.
 */
@Composable
private fun LoadStepPresets(
    uiState: ExerciseEditorUiState,
    onLoadIncrementChanged: (String) -> Unit,
) {
    val currentGrams = ExerciseUiFormatting.kgTextToGrams(uiState.loadIncrementKgText)
    val presetIndex = LOAD_INCREMENT_KG_PRESETS.indexOfFirst { ExerciseUiFormatting.kgTextToGrams(it) == currentGrams }
    var otherChosen by rememberSaveable { mutableStateOf(false) }
    val showOther = otherChosen || (presetIndex < 0 && uiState.loadIncrementKgText.isNotBlank())
    val labels =
        LOAD_INCREMENT_KG_PRESETS.map { stringResource(R.string.exercise_editor_load_step_preset, it) } +
            stringResource(R.string.exercise_editor_preset_other)
    RepFlowPillPicker(
        options = labels,
        selectedIndex = if (showOther) LOAD_INCREMENT_KG_PRESETS.size else presetIndex.takeIf { it >= 0 },
        onSelect = { index ->
            if (index == LOAD_INCREMENT_KG_PRESETS.size) {
                otherChosen = true
            } else {
                otherChosen = false
                onLoadIncrementChanged(if (!showOther && index == presetIndex) "" else LOAD_INCREMENT_KG_PRESETS[index])
            }
        },
        sizing = RepFlowPillPickerDefaults.scale,
    )
    if (showOther) {
        OtherValueField(
            value = uiState.loadIncrementKgText,
            onValueChange = onLoadIncrementChanged,
            label = stringResource(R.string.exercise_editor_load_increment_label),
            error = fieldErrorText(uiState.loadIncrementError),
            keyboardType = KeyboardType.Decimal,
        )
    }
}

@Composable
private fun OtherValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { InlineError(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = FieldShape,
        colors = editorFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapSm),
    )
}

/** `2b`'s technique notes (`:2411`): a 72-tall multiline field with the design's prompt. */
@Composable
private fun InstructionsField(
    uiState: ExerciseEditorUiState,
    onInstructionsChanged: (String) -> Unit,
) {
    val error = fieldErrorText(uiState.instructionsError)
    OutlinedTextField(
        value = uiState.instructions,
        onValueChange = onInstructionsChanged,
        label = { Text(stringResource(R.string.exercise_editor_instructions_label)) },
        placeholder = { Text(stringResource(R.string.exercise_editor_instructions_placeholder)) },
        isError = error != null,
        supportingText = error?.let { { InlineError(it) } },
        minLines = 3,
        shape = FieldShape,
        colors = editorFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(top = SectionGap).heightIn(min = NotesMinHeight),
    )
}

private const val SECONDS_PER_MINUTE = 60L

private val FieldShape = RoundedCornerShape(10.dp)
private val FieldLabelFontSize = 11.5.sp
private val SectionGap = 18.dp
private val NameFieldMinHeight = 52.dp
private val NotesMinHeight = 72.dp
private val SegmentMinHeight = 46.dp
private val SegmentFontSize = 13.5.sp
private val SegmentCheckSize = 11.dp
