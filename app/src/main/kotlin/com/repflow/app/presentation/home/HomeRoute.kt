package com.repflow.app.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route for Home (remediation-1 CP5): owns the ViewModel and hands
 * navigation out through callbacks.
 *
 * **Every `ON_START` pushes the clock's date into the ViewModel**
 * ([HomeViewModel.onForeground]), so a return to the app on a new day shows
 * that day's readiness even when the upstream never restarted - a return
 * inside the 5-second `WhileSubscribed` window, after a deep sleep the
 * midnight wait did not count (plan CP4 item 2).
 *
 * `Resume` and `Finish it` both open the workout surface for now: CP9 re-wires
 * `Finish it` into the finish sheet, the second half of that path to land.
 */
@Composable
fun HomeRoute(
    onOpenWorkout: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogRecoveryClick: () -> Unit,
    onCreatePlanClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
    LaunchedEffect(uiState.openWorkout) {
        if (uiState.openWorkout) {
            viewModel.onWorkoutOpened()
            onOpenWorkout()
        }
    }

    HomeScreen(
        uiState = uiState,
        onSettingsClick = onSettingsClick,
        onResumeClick = onOpenWorkout,
        onFinishClick = onOpenWorkout,
        onAbandonConfirmed = viewModel::onAbandonWorkout,
        onStartWorkout = viewModel::onStartWorkout,
        onCreatePlanClick = onCreatePlanClick,
        onLogRecoveryClick = onLogRecoveryClick,
        onRetryHistory = viewModel::onRetryHistory,
        onErrorShown = viewModel::onErrorShown,
        modifier = modifier,
    )
}
