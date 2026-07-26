package com.repflow.app.presentation.exercise.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.exercise.ExerciseId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ExerciseListScreen] (see plan.md section H).
 */
@Composable
fun ExerciseListRoute(
    onExerciseClick: (ExerciseId) -> Unit,
    onCreateClick: () -> Unit,
    onPlansClick: () -> Unit,
    onWorkoutClick: () -> Unit,
    onRecoveryClick: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseListScreen(
        uiState = uiState,
        onQueryChanged = viewModel::onQueryChanged,
        onFilterChanged = viewModel::onFilterChanged,
        onRetry = viewModel::onRetry,
        onExerciseClick = onExerciseClick,
        onCreateClick = onCreateClick,
        onPlansClick = onPlansClick,
        onWorkoutClick = onWorkoutClick,
        onRecoveryClick = onRecoveryClick,
        onArchiveClicked = viewModel::onArchiveClicked,
        onRestoreClicked = viewModel::onRestoreClicked,
        onUndoArchiveClicked = viewModel::onRestoreClicked,
        onMessageShown = viewModel::onMessageShown,
    )
}
