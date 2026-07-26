package com.repflow.app.presentation.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ActiveWorkoutScreen].
 */
@Composable
fun ActiveWorkoutRoute(viewModel: ActiveWorkoutViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ActiveWorkoutScreen(
        uiState = uiState,
        onStartWorkout = viewModel::onStartWorkout,
        onAddExercise = viewModel::onAddExercise,
        onRecordSet = viewModel::onRecordSet,
        onUndoLastSet = viewModel::onUndoLastSet,
        onEditLastSet = viewModel::onEditLastSet,
        onAddRestTime = { viewModel.onAddRestTime(REST_ADJUST_SECONDS) },
        onRemoveRestTime = { viewModel.onRemoveRestTime(REST_ADJUST_SECONDS) },
        onSkipRestTimer = viewModel::onSkipRestTimer,
        onCompleteWorkout = viewModel::onCompleteWorkout,
        onAbandonWorkout = viewModel::onAbandonWorkout,
        onRetry = viewModel::onErrorShown,
    )
}

private const val REST_ADJUST_SECONDS = 15L
