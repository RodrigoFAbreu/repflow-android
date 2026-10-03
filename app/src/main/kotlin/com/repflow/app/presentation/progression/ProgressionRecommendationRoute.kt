package com.repflow.app.presentation.progression

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route for the recommendation screen (remediation-1 CP6): owns the
 * ViewModel and hands the way out through [onBack]. `Go with the suggestion`
 * and `Done` leave too - going with the suggestion writes nothing, because the
 * recommendation already stands as computed.
 */
@Composable
fun ProgressionRecommendationRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressionRecommendationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressionRecommendationScreen(
        uiState = uiState,
        onBack = onBack,
        onDone = onBack,
        onChooseAnother = viewModel::onChooseAnother,
        onCloseChoices = viewModel::onCloseChoices,
        onPick = viewModel::onPick,
        onKeepSameLoad = viewModel::onKeepSameLoad,
        onRetry = viewModel::onRetry,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}
