package com.repflow.app.presentation.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ActiveWorkoutScreen]. Also schedules/cancels the OS-level rest
 * timer notification alarm ([RestTimerAlarmScheduler]) and requests the
 * `POST_NOTIFICATIONS` runtime permission (Android 13+) the first time a
 * timer starts - purely presentation-layer concerns, kept out of the
 * ViewModel/domain/application layers.
 */
@Composable
fun ActiveWorkoutRoute(viewModel: ActiveWorkoutViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dayContext by viewModel.dayContext.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val restTimer = (uiState.content as? ActiveWorkoutContent.Active)?.restTimer
    LaunchedEffect(restTimer?.endAt) {
        if (restTimer == null) {
            RestTimerAlarmScheduler.cancel(context)
            return@LaunchedEffect
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        RestTimerAlarmScheduler.schedule(context, restTimer.endAt)
    }

    ActiveWorkoutScreen(
        uiState = uiState,
        dayContext = dayContext,
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
