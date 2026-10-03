package com.repflow.app.presentation.exercise.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.exercise.ExerciseId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ExerciseEditorScreen] (see plan.md section H).
 *
 * [onSaved] and [onDismissed] are invoked from `LaunchedEffect`s reacting to
 * the ViewModel's idempotent state flags ([ExerciseEditorUiState.savedExerciseId],
 * [ExerciseEditorUiState.dismissed]) rather than a one-shot event channel, so
 * navigation is driven by stable state that survives recomposition.
 */
@Composable
fun ExerciseEditorRoute(
    onSaved: (ExerciseId) -> Unit,
    onDismissed: () -> Unit,
    viewModel: ExerciseEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.savedExerciseId) {
        uiState.savedExerciseId?.let { id ->
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

    ExerciseEditorScreen(
        uiState = uiState,
        onNameChanged = viewModel::onNameChanged,
        onNameFocusLost = viewModel::onNameFocusLost,
        onTrackingTypeChanged = viewModel::onTrackingTypeChanged,
        onInstructionsChanged = viewModel::onInstructionsChanged,
        onRestSecondsChanged = viewModel::onRestSecondsChanged,
        onLoadIncrementChanged = viewModel::onLoadIncrementChanged,
        onSaveClicked = viewModel::onSaveClicked,
        onBackRequested = viewModel::onBackRequested,
        onDiscardConfirmed = viewModel::onDiscardConfirmed,
        onDiscardCancelled = viewModel::onDiscardCancelled,
        onMessageShown = viewModel::onMessageShown,
    )
}
