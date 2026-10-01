package com.repflow.app.presentation.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutExerciseId

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ActiveWorkoutScreen]. Also schedules/cancels the OS-level rest
 * timer notification alarm ([RestTimerAlarmScheduler]) and requests the
 * `POST_NOTIFICATIONS` runtime permission (Android 13+) the first time a
 * timer starts - purely presentation-layer concerns, kept out of the
 * ViewModel/domain/application layers.
 *
 * The picker row's `Why ›` leaves through [onOpenRecommendation] (the
 * recommendation screen, remediation-1 CP6), and every `ON_START` re-reads the
 * picker's recommendations, so a choice recorded there shows on return.
 *
 * Remediation-1 CP7: [onLeaveWorkout] goes Home with the workout entry
 * removed from the back stack - the leave sheet's `Leave it running and go
 * Home`, and the hand-back once the session has ended. [onCreateExercise]
 * opens the exercise editor from the picker sheet. Which exercise the board
 * has opened is saved here, so a rotation or process restore returns to it.
 */
@Composable
fun ActiveWorkoutRoute(
    onOpenRecommendation: (ExerciseId) -> Unit,
    onCreateExercise: () -> Unit,
    onLeaveWorkout: () -> Unit,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var focusedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onRefreshRecommendations() }
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
        focusedExerciseId = focusedExerciseId?.let(::WorkoutExerciseId),
        onFocusExercise = { id -> focusedExerciseId = id?.value },
        onAddExercise = viewModel::onAddExercise,
        onCreateExercise = onCreateExercise,
        onOpenRecommendation = onOpenRecommendation,
        onRecordSet = viewModel::onRecordSet,
        onUndoLastSet = viewModel::onUndoLastSet,
        onEditLastSet = viewModel::onEditLastSet,
        onAddRestTime = { viewModel.onAddRestTime(REST_ADJUST_SECONDS) },
        onRemoveRestTime = { viewModel.onRemoveRestTime(REST_ADJUST_SECONDS) },
        onSkipRestTimer = viewModel::onSkipRestTimer,
        onCompleteWorkout = viewModel::onCompleteWorkout,
        onLeaveWorkout = onLeaveWorkout,
        onAbandonWorkout = viewModel::onAbandonWorkout,
        onRetry = viewModel::onErrorShown,
    )
}

private const val REST_ADJUST_SECONDS = 15L
