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
import androidx.compose.runtime.rememberUpdatedState
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
import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.Instant

/**
 * Stateful route composable: owns the ViewModel, delegates rendering to the
 * stateless [ActiveWorkoutScreen]. Also schedules/cancels the OS-level rest
 * timer alarm ([RestTimerAlarmScheduler]) and requests the
 * `POST_NOTIFICATIONS` runtime permission (Android 13+) when a timer starts -
 * purely presentation-layer concerns, kept out of the ViewModel/domain/
 * application layers.
 *
 * Remediation-1 CP14: the alarm is still scheduled for **every** running rest,
 * whatever Settings says - the receiver reads the switches when it fires - but
 * the permission is asked for only while the Notification switch is on
 * ([RestTimerPermissionPromptEffect]). The screen gets the settings it applies
 * itself: keep screen awake and confirm before finishing.
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
 *
 * Remediation-1 CP9: [onWorkoutFinished] opens the done screen for the
 * session the finish sheet just completed, and [raiseFinishFromHome] - Home's
 * `Finish it` - opens the surface with the finish sheet already raised.
 */
@Composable
fun ActiveWorkoutRoute(
    onOpenRecommendation: (ExerciseId) -> Unit,
    onCreateExercise: () -> Unit,
    onLeaveWorkout: () -> Unit,
    onWorkoutFinished: (WorkoutSessionId) -> Unit,
    raiseFinishFromHome: Boolean = false,
    viewModel: ActiveWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finish by viewModel.finish.collectAsStateWithLifecycle()
    var focusedExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onRefreshRecommendations() }
    val dayContext by viewModel.dayContext.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val notificationEnabled by viewModel.notificationEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val restTimer = (uiState.content as? ActiveWorkoutContent.Active)?.restTimer
    LaunchedEffect(restTimer?.endAt) {
        if (restTimer == null) {
            RestTimerAlarmScheduler.cancel(context)
            return@LaunchedEffect
        }
        RestTimerAlarmScheduler.schedule(context, restTimer.endAt)
    }
    RestTimerPermissionPromptEffect(
        restTimerEndAt = restTimer?.endAt,
        notificationEnabled = notificationEnabled,
        isPermissionGranted = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        },
        requestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )

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
        finish = finish,
        raiseFinishFromHome = raiseFinishFromHome,
        onWorkoutFinished = onWorkoutFinished,
        keepScreenAwake = settings?.keepScreenAwake == true,
        confirmBeforeFinishing = settings?.confirmBeforeFinishing,
    )
}

/**
 * Asks for `POST_NOTIFICATIONS` when a rest is running and
 * [shouldRequestNotificationPermission] says so (remediation-1 CP14): only
 * while the Notification switch is on, never before it has loaded, and only
 * on Android 13+ without the grant. Keyed on the rest's end and the switch,
 * so a switch that loads (or turns) on while a rest runs still asks once.
 * [sdkInt] is the running device's, overridable so a test can drive both
 * sides of the API 33 line on one device.
 */
@Composable
internal fun RestTimerPermissionPromptEffect(
    restTimerEndAt: Instant?,
    notificationEnabled: Boolean?,
    isPermissionGranted: () -> Boolean,
    requestPermission: () -> Unit,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    val currentIsPermissionGranted by rememberUpdatedState(isPermissionGranted)
    val currentRequestPermission by rememberUpdatedState(requestPermission)
    LaunchedEffect(restTimerEndAt, notificationEnabled) {
        if (restTimerEndAt != null &&
            shouldRequestNotificationPermission(notificationEnabled, sdkInt, currentIsPermissionGranted())
        ) {
            currentRequestPermission()
        }
    }
}

private const val REST_ADJUST_SECONDS = 15L
