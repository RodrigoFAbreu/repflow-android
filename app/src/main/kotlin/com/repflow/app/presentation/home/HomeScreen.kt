package com.repflow.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Home (remediation-1 CP5): `4a`'s Home tab (`RepFlow.dc.html:728-822`), with
 * `1d`'s light theme, empty, error and first-run states (`:2978-3036`) where
 * `4a` is silent. Top to bottom: the header (date, greeting, settings gear),
 * the resume card while a session runs, the start card, the recovery card and
 * the `Last workout` card. The start sheet, the readiness sheet and the abandon
 * confirmation open over it.
 *
 * Not drawn, each a deviation-register row: the plan-day line and the start
 * sheet's day rows (D4), the deload card (D6), `This week` (D9) and the band's
 * advice line (D19). The start card is not drawn while a session runs (D46),
 * because a second session cannot start until that one ends.
 */

private val HeaderGearSize = 44.dp
private val HeaderGearIconSize = 20.dp
private const val HEADER_GEAR_ICON_ALPHA = 0.7f
private val ScreenTopPadding = 18.dp
private val ScreenBottomPadding = 8.dp
private val HeaderBottomGap = 18.dp
private const val GREETING_LETTER_SPACING_EM = -0.015f
private val GreetingLetterSpacing = GREETING_LETTER_SPACING_EM.em

/** What is open over Home. Plain state: it does not need to survive process death. */
private enum class HomeOverlay { NONE, START_SHEET, READINESS_SHEET, ABANDON_CONFIRM }

@Suppress("LongParameterList")
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onSettingsClick: () -> Unit,
    onResumeClick: () -> Unit,
    onFinishClick: () -> Unit,
    onAbandonConfirmed: (WorkoutSessionId) -> Unit,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
    onCreatePlanClick: () -> Unit,
    onLogRecoveryClick: () -> Unit,
    onRetryHistory: () -> Unit,
    onErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overlay by rememberSaveable { mutableStateOf(HomeOverlay.NONE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = uiState.error?.let { stringResource(it.messageRes()) }
    LaunchedEffect(uiState.error) {
        if (errorText != null) {
            snackbarHostState.showSnackbar(errorText)
            onErrorShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = RepFlowSpacing.screenPadding,
                        end = RepFlowSpacing.screenPadding,
                        top = ScreenTopPadding,
                        bottom = ScreenBottomPadding,
                    ),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            HomeHeader(date = uiState.date, onSettingsClick = onSettingsClick)
            val active = uiState.activeWorkout
            if (active != null) {
                ResumeCard(
                    workout = active,
                    onResumeClick = onResumeClick,
                    onFinishClick = onFinishClick,
                    onAbandonClick = { overlay = HomeOverlay.ABANDON_CONFIRM },
                )
            } else {
                StartCard(
                    start = uiState.start,
                    onStartWorkout = onStartWorkout,
                    onTrainSomethingElse = { overlay = HomeOverlay.START_SHEET },
                    onCreatePlanClick = onCreatePlanClick,
                )
            }
            RecoveryCard(
                readiness = uiState.readiness,
                onLogClick = onLogRecoveryClick,
                onScoreClick = { overlay = HomeOverlay.READINESS_SHEET },
            )
            LastWorkoutCard(lastWorkout = uiState.lastWorkout, today = uiState.date, onRetry = onRetryHistory)
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    HomeOverlayHost(
        uiState = uiState,
        overlay = overlay,
        onOverlayChange = { overlay = it },
        onStartWorkout = onStartWorkout,
        onAbandonConfirmed = onAbandonConfirmed,
    )
}

/** Whatever is open over Home: the start sheet, the readiness sheet or the abandon confirmation. */
@Composable
private fun HomeOverlayHost(
    uiState: HomeUiState,
    overlay: HomeOverlay,
    onOverlayChange: (HomeOverlay) -> Unit,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
    onAbandonConfirmed: (WorkoutSessionId) -> Unit,
) {
    val readiness = uiState.readiness
    val session = uiState.activeWorkout?.sessionId
    val close = { onOverlayChange(HomeOverlay.NONE) }
    // An overlay whose subject went away (a session started or ended elsewhere,
    // readiness gone at midnight) closes for good rather than reopening later.
    val stillValid =
        when (overlay) {
            HomeOverlay.NONE -> true
            HomeOverlay.START_SHEET -> session == null
            HomeOverlay.READINESS_SHEET -> readiness is HomeReadiness.Logged
            HomeOverlay.ABANDON_CONFIRM -> session != null
        }
    LaunchedEffect(stillValid) { if (!stillValid) close() }
    when {
        overlay == HomeOverlay.START_SHEET && session == null -> {
            StartSheet(
                options = uiState.startOptions,
                onPick = { versionId ->
                    close()
                    onStartWorkout(versionId)
                },
                onDismissRequest = close,
            )
        }

        overlay == HomeOverlay.READINESS_SHEET && readiness is HomeReadiness.Logged -> {
            ReadinessSheet(readiness = readiness.score, onDismissRequest = close)
        }

        overlay == HomeOverlay.ABANDON_CONFIRM && session != null -> {
            AbandonWorkoutDialog(
                onConfirm = {
                    close()
                    onAbandonConfirmed(session)
                },
                onDismiss = close,
            )
        }
    }
}

/** Date label over the greeting, and the 44dp round settings gear (`:730-736`). */
@Composable
private fun HomeHeader(
    date: LocalDate,
    onSettingsClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = HeaderBottomGap - RepFlowSpacing.gapLg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            RepFlowSectionLabel(text = headerDate(date, LocalConfiguration.current.locales[0]))
            Text(
                text = stringResource(R.string.home_greeting),
                style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = GreetingLetterSpacing),
                modifier = Modifier.padding(top = 2.dp).semantics { heading() },
            )
        }
        IconButton(
            onClick = onSettingsClick,
            modifier =
                Modifier
                    .size(HeaderGearSize)
                    .background(scheme.surface, CircleShape)
                    .border(BorderStroke(1.dp, RepFlowColor.hairline), CircleShape),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.gearSix),
                contentDescription = stringResource(R.string.home_settings_content_description),
                tint = scheme.onSurface.copy(alpha = HEADER_GEAR_ICON_ALPHA),
                modifier = Modifier.size(HeaderGearIconSize),
            )
        }
    }
}

/** `Tuesday, 11 Aug` - the section label upper-cases it, as the design does. */
private fun headerDate(
    date: LocalDate,
    locale: Locale,
): String = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale))

/** `Abandon this workout?` - the same copy CP7's leave sheet confirms with (D17, D18). */
@Composable
private fun AbandonWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_abandon_confirm_title)) },
        text = { Text(stringResource(R.string.workout_abandon_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.workout_abandon_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workout_abandon_keep_action))
            }
        },
    )
}

/** The secondary-text tier, for the cards' meta lines. */
@Composable
internal fun homeMetaColor(): Color = repFlowSecondaryTextColor(MaterialTheme.colorScheme)

private fun HomeErrorReason.messageRes(): Int =
    when (this) {
        HomeErrorReason.ALREADY_ACTIVE -> R.string.home_error_already_active
        HomeErrorReason.PLAN_NOT_FOUND -> R.string.home_error_plan_not_found
        HomeErrorReason.UNKNOWN -> R.string.home_error_unknown
    }
