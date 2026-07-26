package com.repflow.app.presentation.recovery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route composable: owns the ViewModel, delegates rendering to [RecoveryHistoryScreen]. */
@Composable
fun RecoveryHistoryRoute(
    onBackClick: () -> Unit,
    viewModel: RecoveryHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RecoveryHistoryScreen(uiState = uiState, onBackClick = onBackClick)
}
