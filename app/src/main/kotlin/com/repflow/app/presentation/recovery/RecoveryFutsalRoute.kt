package com.repflow.app.presentation.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route composable: owns the ViewModel, delegates rendering to [RecoveryFutsalScreen]. */
@Composable
fun RecoveryFutsalRoute(
    onBack: () -> Unit,
    onHistoryClick: () -> Unit,
    viewModel: RecoveryFutsalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecoveryFutsalScreen(
        uiState = uiState,
        onBack = onBack,
        onHistoryClick = onHistoryClick,
        onDateChanged = viewModel::onDateChanged,
        onScaleFieldChanged = viewModel::onScaleFieldChanged,
        onFutsalPreviousToggled = viewModel::onFutsalPreviousToggled,
        onFutsalNextToggled = viewModel::onFutsalNextToggled,
        onNotesChanged = viewModel::onNotesChanged,
        onDurationChanged = viewModel::onDurationChanged,
        onSessionRpeChanged = viewModel::onSessionRpeChanged,
        onSaveEntry = viewModel::onSaveEntry,
        onMessageShown = viewModel::onMessageShown,
    )
}
