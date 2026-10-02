package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.exercise.editor.InlineError
import com.repflow.app.presentation.exercise.editor.bringIntoViewWhen
import com.repflow.app.presentation.exercise.editor.editorFieldColors
import com.repflow.app.presentation.exercise.editor.notifyOnFocusLost

/**
 * The editor's scrolling body (remediation-1 CP11): the plan name, `4a`'s
 * `Exercises` header with its `N exercises · N working sets` count, the rows
 * ([PlannedExerciseRow]), `4a`'s 52-tall `Add exercise`, the plan-version note,
 * and a refused save's reason.
 */
@Composable
internal fun EditorForm(
    uiState: TrainingPlanEditorUiState,
    contentPadding: PaddingValues,
    onNameChanged: (String) -> Unit,
    onNameFocusLost: () -> Unit,
    rowActions: TrainingPlanEditorRowActions,
    expandedRowId: Long?,
    onRowHeaderClick: (PlannedExerciseRowUiState) -> Unit,
    onChangeExerciseClick: (Long) -> Unit,
    onAddRowClicked: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = RepFlowSpacing.gapXs, bottom = RepFlowSpacing.gapLg),
    ) {
        NameField(uiState, onNameChanged, onNameFocusLost)
        ExercisesHeader(uiState.rows)
        if (uiState.rows.isEmpty()) {
            NoExercisesHint()
        }
        uiState.rows.forEachIndexed { index, row ->
            PlannedExerciseRow(
                row = row,
                expanded = row.rowId == expandedRowId,
                canMoveUp = index > 0,
                canMoveDown = index < uiState.rows.lastIndex,
                actions = rowActions,
                onHeaderClick = { onRowHeaderClick(row) },
                onChangeExerciseClick = { onChangeExerciseClick(row.rowId) },
            )
        }
        RepFlowAccentOutlineButton(
            text = stringResource(R.string.training_plan_editor_add_exercise),
            onClick = onAddRowClicked,
            leadingIcon = RepFlowIcons.plus,
            modifier = Modifier.fillMaxWidth().padding(top = AddButtonTopGap).heightIn(min = AddButtonMinHeight),
        )
        if (uiState.mode is TrainingPlanEditorMode.Edit) {
            VersionNote()
        }
        SubmitErrorText(uiState.submitError)
    }
}

/** The plan name: `2b`'s 52-tall field treatment, the label inside it (`D71`), the inline error under it. */
@Composable
private fun NameField(
    uiState: TrainingPlanEditorUiState,
    onNameChanged: (String) -> Unit,
    onNameFocusLost: () -> Unit,
) {
    val duplicateName = uiState.submitError?.kind == TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME && !uiState.isSaving
    val error =
        fieldErrorText(uiState.visibleNameError)
            ?: if (duplicateName) stringResource(R.string.training_plan_editor_submit_error_duplicate_name) else null
    OutlinedTextField(
        value = uiState.name,
        onValueChange = onNameChanged,
        label = { Text(stringResource(R.string.training_plan_editor_name_label)) },
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
                .bringIntoViewWhen(duplicateName),
    )
}

/** `2a`'s `Exercises` label with `4a`'s count beside it (`:899`, `:3657`). */
@Composable
private fun ExercisesHeader(rows: List<PlannedExerciseRowUiState>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = SectionGap, bottom = RepFlowSpacing.gapSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RepFlowSectionLabel(text = stringResource(R.string.training_plan_editor_exercises_label))
        if (rows.isNotEmpty()) {
            val workingSets = plannedWorkingSetTotal(rows)
            Text(
                text =
                    stringResource(
                        R.string.training_plan_editor_exercises_count,
                        pluralStringResource(R.plurals.training_plan_editor_exercise_count, rows.size, rows.size),
                        pluralStringResource(R.plurals.training_plan_editor_working_set_count, workingSets, workingSets),
                    ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
        }
    }
}

/** `4a`'s empty day (`:901-903`): a hairline ring at radius 12 around the prompt. */
@Composable
private fun NoExercisesHint() {
    Text(
        text = stringResource(R.string.training_plan_editor_no_exercises),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = HintFontSize),
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, RepFlowColor.hairline, HintShape)
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = HintVerticalPadding),
    )
}

/**
 * Plan item 3, `4a`'s note (`:948`): plan versions are immutable and a past
 * workout keeps the version it ran on - an invariant RepFlow already enforces
 * and never said. "From this day" reads "from this plan" (no days, `D4`).
 * Shown when editing an existing plan, the case it describes.
 */
@Composable
private fun VersionNote() {
    val color = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier = Modifier.padding(top = SectionGap),
        horizontalArrangement = Arrangement.spacedBy(NoteGap),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.checkCircle),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = NoteGlyphTopGap).size(NoteGlyphSize),
        )
        Text(
            text = stringResource(R.string.training_plan_editor_version_note),
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun SubmitErrorText(submitError: TrainingPlanEditorSubmitError?) {
    val text =
        when (submitError?.kind) {
            TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME -> null

            // reported against the name field, see NameField
            TrainingPlanEditorSubmitErrorKind.INVALID -> stringResource(R.string.training_plan_editor_submit_error_invalid)

            TrainingPlanEditorSubmitErrorKind.UNAVAILABLE -> stringResource(R.string.training_plan_editor_submit_error_unavailable)

            null -> null
        }
    text?.let {
        Row(modifier = Modifier.padding(top = SectionGap)) {
            InlineError(it)
        }
    }
}

@Composable
internal fun fieldErrorText(error: TrainingPlanEditorFieldError?): String? =
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

private val FieldShape = RoundedCornerShape(10.dp)
private val NameFieldMinHeight = 52.dp
private val SectionGap = 18.dp
private val AddButtonTopGap = 14.dp
private val AddButtonMinHeight = 52.dp
private val HintShape = RoundedCornerShape(12.dp)
private val HintVerticalPadding = 22.dp
private val HintFontSize = 13.5.sp
private val NoteGap = 9.dp
private val NoteGlyphTopGap = 1.dp
private val NoteGlyphSize = 15.dp
