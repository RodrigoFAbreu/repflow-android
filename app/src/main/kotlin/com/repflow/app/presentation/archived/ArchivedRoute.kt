package com.repflow.app.presentation.archived

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Stateful route: owns [ArchivedViewModel], delegates rendering to the stateless [ArchivedScreen]. */
@Composable
fun ArchivedRoute(
    onBack: () -> Unit,
    viewModel: ArchivedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ArchivedScreen(
        uiState = uiState,
        onRestoreClicked = viewModel::onRestoreClicked,
        onUndoRestoreClicked = viewModel::onUndoRestoreClicked,
        onMessageShown = viewModel::onMessageShown,
        onRetry = viewModel::onRetry,
        onBack = onBack,
    )
}
