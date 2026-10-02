package com.repflow.app.presentation.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.R
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
    createdExerciseId: String? = null,
    onCreatedExerciseHandled: () -> Unit = {},
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
    var notificationPromptResolved by remember { mutableIntStateOf(0) }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationPromptResolved++ }

    val restTimer = (uiState.content as? ActiveWorkoutContent.Active)?.restTimer
    RestAlarmEffect(
        restEndAt = restTimer?.endAt,
        schedule = { RestTimerAlarmScheduler.schedule(context, it) },
        cancel = { RestTimerAlarmScheduler.cancel(context) },
    )
    // Coming back from the system's `Alarms & reminders` screen with the grant: re-arm this rest exactly.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (shouldRearmRestAlarm(restTimer?.endAt, Instant.now())) {
            RestTimerAlarmScheduler.schedule(context, checkNotNull(restTimer).endAt)
        }
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

    ExactAlarmPrompt(
        restTimerEndAt = restTimer?.endAt,
        notificationEnabled = notificationEnabled,
        notificationPromptResolved = notificationPromptResolved,
    )

    CreatedExerciseEffect(
        createdExerciseId = createdExerciseId,
        availableExercises = uiState.availableExercises,
        onAdd = viewModel::onAddExercise,
        onHandled = onCreatedExerciseHandled,
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
 * Keeps the OS alarm in step with the rest timer: cancels it when there is no
 * rest, and schedules it for a rest that is still running - never for one whose
 * end has already passed (functional review P-1). A finished rest stays in the
 * session until the next set or `Skip rest`, and a past-due exact alarm fires -
 * and alerts - again at once, whenever the workout is composed again.
 *
 * The one exception is within a single visit: a `-15s` that moves the end from
 * the future into the past while the alarm for the old end is still pending
 * (implementation review round 6, I-1). The rest is over now, so the alert is
 * due now: the single alarm is rescheduled to that past end and fires once,
 * at once, instead of up to 15 s late. The previous end is tracked per
 * composition, so re-entering a screen whose rest had already ended has none
 * and schedules nothing. [now] is overridable so a test can pin the clock.
 */
@Composable
internal fun RestAlarmEffect(
    restEndAt: Instant?,
    schedule: (Instant) -> Unit,
    cancel: () -> Unit,
    now: () -> Instant = Instant::now,
) {
    val currentSchedule by rememberUpdatedState(schedule)
    val currentCancel by rememberUpdatedState(cancel)
    val previousEnd = remember { arrayOfNulls<Instant>(1) }
    LaunchedEffect(restEndAt) {
        val previous = previousEnd[0]
        previousEnd[0] = restEndAt
        val current = now()
        if (restEndAt == null) {
            currentCancel()
        } else if (shouldRearmRestAlarm(restEndAt, current)) {
            currentSchedule(restEndAt)
        } else if (previous != null && previous.isAfter(current)) {
            // The rest was cut short in this visit; its pending alarm is for the old end. Fire it now, once.
            currentSchedule(restEndAt)
        }
    }
}

/**
 * Explains and offers the `Alarms & reminders` grant (functional review J9):
 * without it the rest-end alert is scheduled inexactly and can arrive minutes
 * late - exactly when the phone is locked between sets. Offered **once**, when
 * a rest first runs and exact alarms are not allowed, after any
 * `POST_NOTIFICATIONS` request has been answered so the two never stack.
 * `Not now` is respected for good; the alert keeps working, inexactly.
 */
@Composable
private fun ExactAlarmPrompt(
    restTimerEndAt: Instant?,
    notificationEnabled: Boolean?,
    notificationPromptResolved: Int,
) {
    val context = LocalContext.current
    var visible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(restTimerEndAt, notificationEnabled, notificationPromptResolved) {
        if (restTimerEndAt == null || notificationEnabled == null) return@LaunchedEffect
        val notificationAskPending =
            isNotificationAskPending(
                notificationEnabled,
                Build.VERSION.SDK_INT,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED,
                requestAnswered = notificationPromptResolved > 0,
            )
        if (!notificationAskPending &&
            shouldOfferExactAlarmPrompt(
                Build.VERSION.SDK_INT,
                RestTimerAlarmScheduler.canScheduleExact(context),
                RestTimerAlarmScheduler.exactAlarmPrompted(context),
            )
        ) {
            visible = true
        }
    }
    if (!visible) return

    fun close() {
        RestTimerAlarmScheduler.markExactAlarmPrompted(context)
        visible = false
    }
    AlertDialog(
        onDismissRequest = ::close,
        title = { Text(stringResource(R.string.workout_exact_alarm_title)) },
        text = { Text(stringResource(R.string.workout_exact_alarm_message)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val settings = RestTimerAlarmScheduler.exactAlarmSettingsIntent(context)
                    close()
                    settings?.let { runCatching { context.startActivity(it) } }
                },
            ) { Text(stringResource(R.string.workout_exact_alarm_allow)) }
        },
        dismissButton = {
            TextButton(onClick = ::close) { Text(stringResource(R.string.workout_exact_alarm_not_now)) }
        },
    )
}

/**
 * `Create a new exercise` from the picker (`D56`): once the editor hands back
 * the new exercise's id, adds it to the running workout - as soon as the
 * picker's live list contains it - and reports it handled, so it is added
 * exactly once. Until the list has it, nothing happens and the id stays.
 */
@Composable
internal fun CreatedExerciseEffect(
    createdExerciseId: String?,
    availableExercises: List<ExercisePickerItem>,
    onAdd: (ExercisePickerItem) -> Unit,
    onHandled: () -> Unit,
) {
    val currentOnAdd by rememberUpdatedState(onAdd)
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(createdExerciseId, availableExercises) {
        val item = createdExercisePickerItem(createdExerciseId, availableExercises) ?: return@LaunchedEffect
        currentOnHandled()
        currentOnAdd(item)
    }
}

/** The picker item for the id the editor handed back, or null when there is none or the list does not hold it yet. */
internal fun createdExercisePickerItem(
    createdExerciseId: String?,
    availableExercises: List<ExercisePickerItem>,
): ExercisePickerItem? = createdExerciseId?.let { id -> availableExercises.firstOrNull { it.id.value == id } }

/**
 * Asks for `POST_NOTIFICATIONS` when a rest is running and
 * [shouldRequestNotificationPermission] says so (remediation-1 CP14): only
 * while the Notification switch is on, never before it has loaded, and only
 * on Android 13+ without the grant. Keyed on the rest's end and the switch,
 * so a switch that loads (or turns) on while a rest runs still asks once. A rest
 * that is merely adjusted (`+/-15s`) keeps running and never asks again; the
 * next rest start does (functional review R2-F-5).
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
    val restRunning = restTimerEndAt != null
    LaunchedEffect(restRunning, notificationEnabled) {
        if (restRunning &&
            shouldRequestNotificationPermission(notificationEnabled, sdkInt, currentIsPermissionGranted())
        ) {
            currentRequestPermission()
        }
    }
}

private const val REST_ADJUST_SECONDS = 15L
