package com.repflow.app.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route composable: owns the ViewModel, delegates rendering to [ProgressScreen]. */
@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(
        uiState = uiState,
        onExerciseSelected = viewModel::onExerciseSelected,
        onMetricSelected = viewModel::onMetricSelected,
    )
}
