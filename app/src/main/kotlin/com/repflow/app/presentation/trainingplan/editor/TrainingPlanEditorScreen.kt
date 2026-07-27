package com.repflow.app.presentation.trainingplan.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.repflow.app.R

/**
 * Stateless plan editor screen: state in, events out, no Hilt. Mirrors
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorScreen]'s
 * shape; the row-level callbacks are grouped into [TrainingPlanEditorRowActions]
 * to keep this and [EditorForm]'s parameter counts within Detekt's
 * `LongParameterList` threshold.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackRequested)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(editorTitle(uiState.mode)) },
                navigationIcon = { BackNavigationIcon(onBackRequested) },
            )
        },
    ) { innerPadding ->
        EditorBody(uiState, innerPadding, onNameChanged, rowActions, onAddRowClicked, onSaveClicked)
    }

    if (uiState.isDiscardDialogVisible) {
        DiscardDialog(onDiscardConfirmed, onDiscardCancelled)
    }
}

@Composable
private fun editorTitle(mode: TrainingPlanEditorMode): String =
    when (mode) {
        is TrainingPlanEditorMode.Create -> stringResource(R.string.training_plan_editor_title_create)
        is TrainingPlanEditorMode.Edit -> stringResource(R.string.training_plan_editor_title_edit)
    }

@Composable
private fun BackNavigationIcon(onBackRequested: () -> Unit) {
    val contentDescription = stringResource(R.string.training_plan_editor_back_content_description)
    IconButton(
        onClick = onBackRequested,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text("<")
    }
}

@Composable
private fun EditorBody(
    uiState: TrainingPlanEditorUiState,
    innerPadding: PaddingValues,
    onNameChanged: (String) -> Unit,
    rowActions: TrainingPlanEditorRowActions,
    onAddRowClicked: () -> Unit,
    onSaveClicked: () -> Unit,
) {
    when (uiState.loadStatus) {
        TrainingPlanEditorLoadStatus.LOADING -> {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        TrainingPlanEditorLoadStatus.NOT_FOUND -> {
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.training_plan_editor_not_found))
            }
        }

        TrainingPlanEditorLoadStatus.READY -> {
            EditorForm(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onNameChanged = onNameChanged,
                rowActions = rowActions,
                onAddRowClicked = onAddRowClicked,
                onSaveClicked = onSaveClicked,
            )
        }
    }
}

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
