package com.repflow.app.presentation.trainingplan.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [TrainingPlanListScreen].
 */
@Composable
fun TrainingPlanListRoute(
    onPlanClick: (TrainingPlanId) -> Unit,
    onCreateClick: () -> Unit,
    viewModel: TrainingPlanListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TrainingPlanListScreen(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onPlanClick = onPlanClick,
        onCreateClick = onCreateClick,
        onFilterChanged = viewModel::onFilterChanged,
        onArchiveClicked = viewModel::onArchiveClicked,
        onRestoreClicked = viewModel::onRestoreClicked,
        onUndoArchiveClicked = viewModel::onRestoreClicked,
        onMessageShown = viewModel::onMessageShown,
    )
}
