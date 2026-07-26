package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [TrainingPlanEditorScreen]. Mirrors
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorRoute]'s
 * navigation-via-stable-state pattern.
 */
@Composable
fun TrainingPlanEditorRoute(
    onSaved: (TrainingPlanId) -> Unit,
    onDismissed: () -> Unit,
    viewModel: TrainingPlanEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedPlanId) {
        uiState.savedPlanId?.let { id ->
            viewModel.onSaveNavigationHandled()
            onSaved(id)
        }
    }
    LaunchedEffect(uiState.dismissed) {
        if (uiState.dismissed) {
            viewModel.onDismissHandled()
            onDismissed()
        }
    }

    val rowActions =
        TrainingPlanEditorRowActions(
            onExerciseSelected = viewModel::onRowExerciseSelected,
            onTargetSetsChanged = viewModel::onRowTargetSetsChanged,
            onRepMinChanged = viewModel::onRowRepMinChanged,
            onRepMaxChanged = viewModel::onRowRepMaxChanged,
            onDurationMinChanged = viewModel::onRowDurationMinChanged,
            onDurationMaxChanged = viewModel::onRowDurationMaxChanged,
            onRestSecondsChanged = viewModel::onRowRestSecondsChanged,
            onOptionalChanged = viewModel::onRowOptionalChanged,
            onMoveUp = viewModel::onMoveRowUp,
            onMoveDown = viewModel::onMoveRowDown,
            onRemove = viewModel::onRemoveRowClicked,
        )

    TrainingPlanEditorScreen(
        uiState = uiState,
        onNameChanged = viewModel::onNameChanged,
        rowActions = rowActions,
        onAddRowClicked = viewModel::onAddRowClicked,
        onSaveClicked = viewModel::onSaveClicked,
        onBackRequested = viewModel::onBackRequested,
        onDiscardConfirmed = viewModel::onDiscardConfirmed,
        onDiscardCancelled = viewModel::onDiscardCancelled,
    )
}
