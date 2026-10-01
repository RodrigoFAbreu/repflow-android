package com.repflow.app.presentation.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import com.repflow.app.presentation.progression.labelRes

/**
 * Stateless workout surface: state in, events out. Since remediation-1 CP7 it
 * is `4a`'s workout mode - the **board** (one row per exercise, `X` / title
 * with the elapsed clock / `Finish`) - and, for the exercise the board opened
 * ([focusedExerciseId]), that exercise's set entry. CP8 converts the set entry
 * into `4a`'s focus screen; until then it is the existing per-exercise card
 * under a sub-screen bar whose back arrow returns to the board.
 *
 * **Workout mode replaces the nav, and an `X` or `Finish` is the only way out**
 * (`6b`). The `X` - and the system back gesture on the board - open the leave
 * sheet rather than popping the back stack; back from the set entry returns
 * to the board. Leaving calls no use case ([onLeaveWorkout] only navigates),
 * and abandoning sits behind its own destructive confirmation (`D17`, `D18`).
 * When the session ends - abandoned or finished - there is no workout surface
 * left to show, so the screen hands back to Home through [onLeaveWorkout] too.
 *
 * Starting a workout is Home's alone (remediation-1 CP5); this surface no
 * longer carries a start menu.
 */
@Suppress("LongParameterList")
@Composable
fun ActiveWorkoutScreen(
    uiState: ActiveWorkoutUiState,
    dayContext: WorkoutDayContextUi?,
    focusedExerciseId: WorkoutExerciseId?,
    onFocusExercise: (WorkoutExerciseId?) -> Unit,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onCreateExercise: () -> Unit,
    onOpenRecommendation: (ExerciseId) -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onAddRestTime: () -> Unit,
    onRemoveRestTime: () -> Unit,
    onSkipRestTimer: () -> Unit,
    onCompleteWorkout: (WorkoutSessionId) -> Unit,
    onLeaveWorkout: () -> Unit,
    onAbandonWorkout: (WorkoutSessionId) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = uiState.content) {
            is ActiveWorkoutContent.Loading -> {
                RepFlowLoadingIndicator()
            }

            is ActiveWorkoutContent.NoActiveSession -> {
                LaunchedEffect(Unit) { onLeaveWorkout() }
                RepFlowLoadingIndicator()
            }

            is ActiveWorkoutContent.Active -> {
                WorkoutMode(
                    content = content,
                    availableExercises = uiState.availableExercises,
                    dayContext = dayContext,
                    focusedExerciseId = focusedExerciseId,
                    onFocusExercise = onFocusExercise,
                    onAddExercise = onAddExercise,
                    onCreateExercise = onCreateExercise,
                    onOpenRecommendation = onOpenRecommendation,
                    onRecordSet = onRecordSet,
                    onUndoLastSet = onUndoLastSet,
                    onEditLastSet = onEditLastSet,
                    restStrip = {
                        content.restTimer?.let { timer ->
                            RestTimerBar(timer, onAddRestTime, onRemoveRestTime, onSkipRestTimer)
                        }
                    },
                    onCompleteWorkout = onCompleteWorkout,
                    onLeaveWorkout = onLeaveWorkout,
                    onAbandonWorkout = onAbandonWorkout,
                )
            }

            is ActiveWorkoutContent.ObservationFailed -> {
                RepFlowFailureState(
                    message = stringResource(R.string.workout_active_observation_failed),
                    retryLabel = stringResource(R.string.workout_active_retry),
                    onRetry = onRetry,
                )
            }
        }
    }
}

/** What is drawn over workout mode: at most one sheet or dialog at a time. */
private enum class WorkoutOverlay { NONE, LEAVE_SHEET, ABANDON_CONFIRM, EXERCISE_PICKER }

@Suppress("LongParameterList")
@Composable
private fun WorkoutMode(
    content: ActiveWorkoutContent.Active,
    availableExercises: List<ExercisePickerItem>,
    dayContext: WorkoutDayContextUi?,
    focusedExerciseId: WorkoutExerciseId?,
    onFocusExercise: (WorkoutExerciseId?) -> Unit,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onCreateExercise: () -> Unit,
    onOpenRecommendation: (ExerciseId) -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    restStrip: @Composable () -> Unit,
    onCompleteWorkout: (WorkoutSessionId) -> Unit,
    onLeaveWorkout: () -> Unit,
    onAbandonWorkout: (WorkoutSessionId) -> Unit,
) {
    var overlay by rememberSaveable { mutableStateOf(WorkoutOverlay.NONE) }
    val close = { overlay = WorkoutOverlay.NONE }
    val focused = focusedExerciseId?.let { id -> content.exercises.find { it.id == id } }

    // A sheet or dialog handles back itself (it closes); with neither open,
    // back leaves the set entry for the board, and the board for the leave
    // sheet - never the back stack.
    BackHandler(enabled = overlay == WorkoutOverlay.NONE) {
        if (focused != null) onFocusExercise(null) else overlay = WorkoutOverlay.LEAVE_SHEET
    }

    if (focused != null) {
        ExerciseSetEntry(
            exercise = focused,
            onBackToBoard = { onFocusExercise(null) },
            onRecordSet = onRecordSet,
            onUndoLastSet = onUndoLastSet,
            onEditLastSet = onEditLastSet,
            restStrip = restStrip,
        )
    } else {
        WorkoutBoard(
            content = content,
            dayContext = dayContext,
            onLeaveClick = { overlay = WorkoutOverlay.LEAVE_SHEET },
            onFinishClick = { onCompleteWorkout(content.sessionId) },
            onExerciseClick = { id -> onFocusExercise(id) },
            onAddExerciseClick = { overlay = WorkoutOverlay.EXERCISE_PICKER },
            restStrip = restStrip,
        )
    }

    when (overlay) {
        WorkoutOverlay.NONE -> {
            Unit
        }

        WorkoutOverlay.LEAVE_SHEET -> {
            LeaveWorkoutSheet(
                onDismissRequest = close,
                onLeaveRunning = {
                    close()
                    onLeaveWorkout()
                },
                onAbandon = { overlay = WorkoutOverlay.ABANDON_CONFIRM },
            )
        }

        WorkoutOverlay.ABANDON_CONFIRM -> {
            AbandonWorkoutDialog(
                onConfirm = {
                    close()
                    onAbandonWorkout(content.sessionId)
                },
                onDismiss = close,
            )
        }

        WorkoutOverlay.EXERCISE_PICKER -> {
            ExercisePickerSheet(
                availableExercises = availableExercises,
                onDismissRequest = close,
                onAddExercise = { exercise ->
                    close()
                    onAddExercise(exercise)
                },
                onCreateExercise = {
                    close()
                    onCreateExercise()
                },
                onOpenRecommendation = { id ->
                    close()
                    onOpenRecommendation(id)
                },
            )
        }
    }
}

/**
 * One exercise's set entry, opened from its board row. Remediation-1 CP7
 * hosts the existing [ExerciseCard] here unchanged; CP8 replaces it with
 * `4a`'s focus screen (steppers, keypad, scale rows, pinned bottom bar).
 */
@Composable
private fun ExerciseSetEntry(
    exercise: ActiveExerciseUi,
    onBackToBoard: () -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    restStrip: @Composable () -> Unit,
) {
    RepFlowScreenScaffold(
        title = exercise.name,
        onBack = onBackToBoard,
        backContentDescription = stringResource(R.string.workout_focus_back_content_description),
        bottomBar = restStrip,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            item(key = exercise.id.value) {
                ExerciseCard(exercise, onRecordSet, onUndoLastSet, onEditLastSet)
            }
        }
    }
}

/**
 * The rest strip (`4a` board and focus, `:1220-1237`): the countdown, what it
 * is for, a 4dp bar, a dismiss `X`, and `-15s` / `+15s` / `Skip rest`.
 *
 * **The tick loop below is untouched.** The countdown is still derived on
 * every tick from the absolute [RestTimerUi.endAt] against `Instant.now()`,
 * so a recomposition after process death still reconstructs the correct
 * value with no drift. Remediation-1 CP7 changes only what the loop renders:
 * at zero the strip reads `Rest done` / `Next set is ready` - the prototype's
 * own zero state (`nRestLabel`, `nRestSub`) - instead of a bare `0:00`. The
 * dismiss `X` and `Skip rest` both end the rest (the prototype wires both to
 * `nRestSkip`); the three callbacks are unchanged. The strip names no
 * exercise and draws no lit fill at zero, and there is no separate `Rest
 * complete` banner (`D58`).
 */
@Composable
private fun RestTimerBar(
    timer: RestTimerUi,
    onAddRestTime: () -> Unit,
    onRemoveRestTime: () -> Unit,
    onSkipRestTimer: () -> Unit,
) {
    var remainingSeconds by remember(timer.endAt) {
        mutableStateOf(
            (
                timer.endAt.epochSecond -
                    java.time.Instant
                        .now()
                        .epochSecond
            ).coerceAtLeast(0),
        )
    }
    androidx.compose.runtime.LaunchedEffect(timer.endAt) {
        while (remainingSeconds > 0) {
            kotlinx.coroutines.delay(TICK_INTERVAL_MILLIS)
            remainingSeconds =
                (
                    timer.endAt.epochSecond -
                        java.time.Instant
                            .now()
                            .epochSecond
                ).coerceAtLeast(0)
        }
    }
    val resting = remainingSeconds > 0
    val dismissDescription = stringResource(R.string.workout_rest_dismiss_content_description)
    RepFlowCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = RestStripSideMargin, end = RestStripSideMargin, bottom = RestStripBottomMargin),
        contentPadding = PaddingValues(horizontal = RestStripHorizontalPadding, vertical = RestStripVerticalPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
            ) {
                Text(
                    text =
                        if (resting) {
                            stringResource(
                                R.string.workout_rest_remaining,
                                remainingSeconds / MINUTE_SECONDS,
                                remainingSeconds % MINUTE_SECONDS,
                            )
                        } else {
                            stringResource(R.string.workout_rest_done)
                        },
                    style =
                        RepFlowNumericTextStyle.copy(
                            fontSize = RestTimerCountdownFontSize,
                            lineHeight = RestTimerCountdownLineHeight,
                        ),
                    color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = RestTimerCountdownMinWidth),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RestStripSubGap)) {
                    Text(
                        text = stringResource(if (resting) R.string.workout_rest_resting else R.string.workout_rest_ready),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = RestStripSubFontSize),
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { restTimerProgress(remainingSeconds, timer.totalDurationSeconds) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(RestTimerTrackHeight)
                                .clip(RepFlowShapes.pill),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = RepFlowColor.control,
                        strokeCap = StrokeCap.Round,
                        gapSize = RestTimerTrackGap,
                        drawStopIndicator = {},
                    )
                }
                IconButton(
                    onClick = onSkipRestTimer,
                    modifier = Modifier.semantics { contentDescription = dismissDescription },
                ) {
                    Icon(
                        painter = painterResource(RepFlowIcons.x),
                        contentDescription = null,
                        modifier = Modifier.size(RestTimerIconSize),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.workout_active_rest_timer_remove),
                    onClick = onRemoveRestTime,
                    modifier = Modifier.weight(1f),
                )
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.workout_active_rest_timer_add),
                    onClick = onAddRestTime,
                    modifier = Modifier.weight(1f),
                )
                RepFlowPrimaryButton(
                    text = stringResource(R.string.workout_rest_skip),
                    onClick = onSkipRestTimer,
                    modifier = Modifier.weight(1f).height(RestStripButtonHeight),
                )
            }
        }
    }
}

/**
 * How much of the planned rest is still to run, as the progress bar's own
 * 0..1 fraction.
 *
 * Derived, never stored: the countdown itself still comes from the absolute
 * `endAt` tick loop above, and this only re-expresses it. A timer with no
 * recorded total reads as fully elapsed rather than dividing by zero.
 */
internal fun restTimerProgress(
    remainingSeconds: Long,
    totalDurationSeconds: Int,
): Float =
    if (totalDurationSeconds <= 0) {
        PROGRESS_MIN
    } else {
        (remainingSeconds.toFloat() / totalDurationSeconds).coerceIn(PROGRESS_MIN, PROGRESS_MAX)
    }

private const val MINUTE_SECONDS = 60L
private const val TICK_INTERVAL_MILLIS = 1_000L
private const val PROGRESS_MIN = 0f
private const val PROGRESS_MAX = 1f

/** The design's tabular rest countdown, 24/500, at least 88 wide. */
private val RestTimerCountdownFontSize = 24.sp

private val RestTimerCountdownLineHeight = 30.sp

private val RestTimerCountdownMinWidth = 88.dp

private val RestTimerIconSize = 18.dp

/** The design's 4px bar. */
private val RestTimerTrackHeight = 4.dp

/** No inset between indicator and track: the design draws one continuous bar. */
private val RestTimerTrackGap = 0.dp

/** `margin:0 12px 10px` and `padding:12px 14px`. */
private val RestStripSideMargin = 12.dp
private val RestStripBottomMargin = 10.dp
private val RestStripHorizontalPadding = 14.dp
private val RestStripVerticalPadding = 12.dp
private val RestStripSubGap = 6.dp
private val RestStripSubFontSize = 12.sp

/** The strip's three buttons are one row at `6b`'s 44 floor, `Skip rest` included. */
private val RestStripButtonHeight = 44.dp

/**
 * The picker row's recommendation summary: the result in force, the policy's
 * top reason and the overridden marker, then `Why ›` into the recommendation
 * screen (remediation-1 CP6). The three inline override buttons this row used
 * to carry moved there - the override is recorded on that screen, through the
 * same `RecordManualOverride` - so the row is a way in, not a second place to
 * decide. CP7 carried the row from the old `DropdownMenu` into the picker
 * sheet exactly as it stood.
 */
@Composable
internal fun RecommendationRow(
    exerciseName: String,
    recommendation: ProgressionRecommendationUi,
    onWhyClick: () -> Unit,
) {
    Text(
        text =
            stringResource(recommendation.result.labelRes()) +
                (recommendation.topReason?.let { " — $it" } ?: "") +
                if (recommendation.isOverridden) " (${stringResource(R.string.progression_overridden)})" else "",
        style = MaterialTheme.typography.bodySmall,
    )
    val whyDescription = stringResource(R.string.progression_why_content_description, exerciseName)
    TextButton(
        onClick = onWhyClick,
        modifier =
            Modifier
                .heightIn(min = WhyLinkMinHeight)
                .semantics { contentDescription = whyDescription },
    ) {
        Text(stringResource(R.string.progression_why_link), style = MaterialTheme.typography.labelLarge)
        Icon(
            painter = painterResource(RepFlowIcons.caretRight),
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp).size(WhyLinkCaretSize),
        )
    }
}

/** `6b`'s 44 tap-target floor. */
private val WhyLinkMinHeight = 44.dp

private val WhyLinkCaretSize = 12.dp
