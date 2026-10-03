package com.repflow.app.presentation.trainingplan.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [TrainingPlanListScreen]. A card's `Start workout` opens the
 * workout through [onOpenWorkout] once the session exists, clearing the flag
 * first so a recomposition never navigates twice (Home's route does the same).
 */
@Composable
fun TrainingPlanListRoute(
    onPlanClick: (TrainingPlanId) -> Unit,
    onCreateClick: () -> Unit,
    onOpenWorkout: () -> Unit,
    viewModel: TrainingPlanListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.openWorkout) {
        if (uiState.openWorkout) {
            viewModel.onWorkoutOpened()
            onOpenWorkout()
        }
    }

    TrainingPlanListScreen(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onPlanClick = onPlanClick,
        onStartClick = viewModel::onStartClicked,
        onCreateClick = onCreateClick,
        onFilterChanged = viewModel::onFilterChanged,
        onArchiveClicked = viewModel::onArchiveClicked,
        onRestoreClicked = viewModel::onRestoreClicked,
        onUndoArchiveClicked = viewModel::onRestoreClicked,
        onMessageShown = viewModel::onMessageShown,
    )
}
