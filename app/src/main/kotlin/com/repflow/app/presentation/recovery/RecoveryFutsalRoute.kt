package com.repflow.app.presentation.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route composable: owns the ViewModel, delegates rendering to [RecoveryFutsalScreen]. */
@Composable
fun RecoveryFutsalRoute(viewModel: RecoveryFutsalViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecoveryFutsalScreen(
        uiState = uiState,
        onScaleFieldChanged = viewModel::onScaleFieldChanged,
        onFutsalPreviousToggled = viewModel::onFutsalPreviousToggled,
        onFutsalNextToggled = viewModel::onFutsalNextToggled,
        onNotesChanged = viewModel::onNotesChanged,
        onSaveRecovery = viewModel::onSaveRecovery,
        onDurationChanged = viewModel::onDurationChanged,
        onSessionRpeChanged = viewModel::onSessionRpeChanged,
        onSaveFutsal = viewModel::onSaveFutsal,
        onMessageShown = viewModel::onMessageShown,
    )
}
