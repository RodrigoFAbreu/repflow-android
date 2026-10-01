package com.repflow.app.presentation.trainingplan.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold

/**
 * Stateless plan editor screen: state in, events out, no Hilt.
 *
 * `4a`'s plan edit composition with `2a`'s target controls (remediation-1
 * CP11), in CP3's sub-screen frame: the name, an `Exercises` count, one row per
 * planned exercise that **expands in place** into steppers (working sets,
 * warm-up sets, the rep or duration range, rest with `2a`'s presets) and an
 * `Optional` toggle, `Add exercise` opening the exercise picker as a sheet, the
 * plan-version note, and `Save` pinned to the bottom action bar. Row-level
 * callbacks stay grouped in [TrainingPlanEditorRowActions]; the ViewModel's
 * input contract is unchanged.
 *
 * Which row is expanded and which row the picker is choosing for are screen
 * state, not ViewModel state. `Add exercise` still asks the ViewModel for a new
 * empty row ([onAddRowClicked]); the picker then opens for that row as soon as
 * it appears, and choosing an exercise fills it through the existing
 * `onExerciseSelected` and expands it.
 */
@Composable
fun TrainingPlanEditorScreen(
    uiState: TrainingPlanEditorUiState,
    onNameChanged: (String) -> Unit,
    rowActions: TrainingPlanEditorRowActions,
    onAddRowClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onBackRequested: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
    onCreateExerciseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackRequested)

    var expandedRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerRowId by rememberSaveable { mutableStateOf<Long?>(null) }
    // While non-null, `Add exercise` is waiting for its new row: any row id above this one.
    var awaitingRowAfter by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(uiState.rows) {
        val after = awaitingRowAfter ?: return@LaunchedEffect
        uiState.rows.firstOrNull { it.rowId > after && it.exerciseId == null }?.let { newRow ->
            pickerRowId = newRow.rowId
            awaitingRowAfter = null
        }
    }

    val isReady = uiState.loadStatus == TrainingPlanEditorLoadStatus.READY
    RepFlowScreenScaffold(
        title = stringResource(editorTitleRes(uiState.mode)),
        modifier = modifier,
        onBack = onBackRequested,
        backContentDescription = stringResource(R.string.training_plan_editor_back_content_description),
        bottomBar = { if (isReady) SaveBar(uiState, onSaveClicked) },
    ) { padding ->
        when (uiState.loadStatus) {
            TrainingPlanEditorLoadStatus.LOADING -> {
                RepFlowLoadingIndicator(modifier = Modifier.padding(padding))
            }

            TrainingPlanEditorLoadStatus.NOT_FOUND -> {
                RepFlowEmptyState(
                    message = stringResource(R.string.training_plan_editor_not_found),
                    modifier = Modifier.padding(padding),
                )
            }

            TrainingPlanEditorLoadStatus.READY -> {
                EditorForm(
                    uiState = uiState,
                    contentPadding = padding,
                    onNameChanged = onNameChanged,
                    rowActions = rowActions,
                    expandedRowId = expandedRowId,
                    onRowHeaderClick = { row ->
                        if (row.exerciseId == null) {
                            pickerRowId = row.rowId
                        } else {
                            expandedRowId = if (expandedRowId == row.rowId) null else row.rowId
                        }
                    },
                    onChangeExerciseClick = { rowId -> pickerRowId = rowId },
                    onAddRowClicked = {
                        awaitingRowAfter = uiState.rows.maxOfOrNull { it.rowId } ?: NO_ROW_ID
                        onAddRowClicked()
                    },
                )
            }
        }
    }

    val pickingFor = pickerRowId
    if (isReady && pickingFor != null) {
        ExercisePickerSheet(
            options = uiState.availableExercises,
            onSelect = { exerciseId ->
                rowActions.onExerciseSelected(pickingFor, exerciseId)
                expandedRowId = pickingFor
                pickerRowId = null
            },
            onCreateExerciseClick = {
                pickerRowId = null
                onCreateExerciseClick()
            },
            onDismissRequest = { pickerRowId = null },
        )
    }

    if (uiState.isDiscardDialogVisible) {
        DiscardDialog(onDiscardConfirmed, onDiscardCancelled)
    }
}

private fun editorTitleRes(mode: TrainingPlanEditorMode): Int =
    when (mode) {
        is TrainingPlanEditorMode.Create -> R.string.training_plan_editor_title_create
        is TrainingPlanEditorMode.Edit -> R.string.training_plan_editor_title_edit
    }

/**
 * `Save` (`Saving…` while in flight) as the 56 primary on the bottom action
 * bar, enabled exactly when the draft is saveable - `D70`'s answer for the
 * exercise editor, applied to `2a`'s pinned `Save as version N` bar.
 */
@Composable
private fun SaveBar(
    uiState: TrainingPlanEditorUiState,
    onSaveClicked: () -> Unit,
) {
    RepFlowBottomActionBar(
        primaryText =
            stringResource(
                if (uiState.isSaving) R.string.training_plan_editor_saving else R.string.training_plan_editor_save,
            ),
        onPrimaryClick = onSaveClicked,
        primaryEnabled = uiState.isSaveEnabled,
    )
}

/** Discarding unsaved edits is destructive, so it stays a dialog (`6b`: dialogs only for destructive confirmation). */
@Composable
private fun DiscardDialog(
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDiscardCancelled,
        title = { Text(stringResource(R.string.training_plan_editor_discard_dialog_title)) },
        text = { Text(stringResource(R.string.training_plan_editor_discard_dialog_body)) },
        confirmButton = {
            Button(onClick = onDiscardConfirmed) { Text(stringResource(R.string.training_plan_editor_discard_dialog_confirm)) }
        },
        dismissButton = {
            Button(onClick = onDiscardCancelled) { Text(stringResource(R.string.training_plan_editor_discard_dialog_cancel)) }
        },
    )
}

/** Below every real row id, so the first row added to an empty plan is still "after" it. */
private const val NO_ROW_ID = -1L
