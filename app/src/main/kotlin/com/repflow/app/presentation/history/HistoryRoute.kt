package com.repflow.app.presentation.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route composable: owns the ViewModel, delegates rendering to [HistoryScreen]. */
@Composable
fun HistoryRoute(viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState = uiState,
        onSessionClick = viewModel::onSessionClick,
        onDetailDismissed = viewModel::onDetailDismissed,
        onInvalidateClicked = viewModel::onInvalidateClicked,
        onExerciseFilterChanged = viewModel::onExerciseFilterChanged,
        onPlanFilterChanged = viewModel::onPlanFilterChanged,
        onStartDateChanged = viewModel::onStartDateChanged,
        onEndDateChanged = viewModel::onEndDateChanged,
        onShowInvalidatedChanged = viewModel::onShowInvalidatedChanged,
        onSortOrderChanged = viewModel::onSortOrderChanged,
        onMessageShown = viewModel::onMessageShown,
    )
}
