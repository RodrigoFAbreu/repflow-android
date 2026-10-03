package com.repflow.app.presentation.history

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.history.workoutSummaryOf
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowListRowDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.durationMinutes
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stateless History screen (remediation-1 CP12, `3a`'s list in CP3's
 * top-level frame): the `History` title, the filter chips over the existing
 * `HistoryFilters`, `N workouts · newest first` (the order word is the sort
 * toggle), month sections, and one 68-tall row per session - its name with a
 * `PR` and an `invalidated` badge, `<when> · <time> · N sets · <volume>`, and a
 * caret. Tapping a row opens its detail ([HistoryDetailScreen]), which is
 * where invalidating a workout now lives (`3a`'s `⋮`).
 */
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onSessionClick: (WorkoutSessionId) -> Unit,
    onDetailDismissed: () -> Unit,
    onInvalidateClicked: (WorkoutSessionId) -> Unit,
    onExerciseFilterChanged: (ExerciseId?) -> Unit,
    onPlanFilterChanged: (HistoryPlanFilter) -> Unit,
    onStartDateChanged: (LocalDate?) -> Unit,
    onEndDateChanged: (LocalDate?) -> Unit,
    onShowInvalidatedChanged: (Boolean) -> Unit,
    onSortOrderChanged: (HistorySortOrder) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    HistoryMessages(uiState.messages, snackbarHostState, onMessageShown)
    val snackbarHost: @Composable () -> Unit = { SnackbarHost(snackbarHostState) { HistorySnackbar(it) } }

    // Hoisted above the detail's early return: the list leaves composition while a
    // detail is open, so its scroll position has to live here to survive Back (9c, 9e).
    val listState = rememberLazyListState()
    val selectedSession = uiState.selectedSession
    val summary =
        remember(selectedSession, uiState.sessions) {
            selectedSession?.let { workoutSummaryOf(it.id, uiState.sessions) }
        }
    if (selectedSession != null && summary != null) {
        HistoryDetailScreen(
            summary = summary,
            planLabel = selectedSession.trainingPlanVersionId?.let { uiState.versionLabels[it] },
            onBackClick = onDetailDismissed,
            onInvalidateConfirmed = { onInvalidateClicked(selectedSession.id) },
            modifier = modifier,
            snackbarHost = snackbarHost,
        )
        return
    }

    RepFlowScreenScaffold(
        title = stringResource(R.string.history_title),
        modifier = modifier,
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HistoryFilterChips(
                uiState = uiState,
                onExerciseFilterChanged = onExerciseFilterChanged,
                onPlanFilterChanged = onPlanFilterChanged,
                onStartDateChanged = onStartDateChanged,
                onEndDateChanged = onEndDateChanged,
                onShowInvalidatedChanged = onShowInvalidatedChanged,
                modifier = Modifier.padding(top = ChipsTopGap),
            )
            when {
                uiState.isLoading -> {
                    RepFlowLoadingIndicator()
                }

                uiState.sessions.isEmpty() -> {
                    RepFlowEmptyState(message = stringResource(R.string.history_empty), icon = RepFlowIcons.clockCounterClockwise)
                }

                else -> {
                    val visibleSessions = uiState.visibleSessions
                    CountLine(
                        count = visibleSessions.size,
                        sortOrder = uiState.filters.sortOrder,
                        onSortOrderChanged = onSortOrderChanged,
                    )
                    if (visibleSessions.isEmpty()) {
                        RepFlowEmptyState(message = stringResource(R.string.history_empty_no_matches))
                    } else {
                        SessionList(
                            sessions = visibleSessions,
                            uiState = uiState,
                            onSessionClick = onSessionClick,
                            listState = listState,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows queued messages one at a time with a finite duration, consuming each
 * once shown. Hosted above both the list and the detail, so a message raised
 * while the detail is open is not held back until it closes.
 */
@Composable
private fun HistoryMessages(
    messages: List<HistoryMessage>,
    snackbarHostState: SnackbarHostState,
    onMessageShown: (Long) -> Unit,
) {
    val invalidatedText = stringResource(R.string.history_message_invalidated)
    val operationFailedText = stringResource(R.string.history_message_operation_failed)
    val message = messages.firstOrNull()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val text =
            when (current) {
                is HistoryMessage.Invalidated -> invalidatedText
                is HistoryMessage.OperationFailed -> operationFailedText
            }
        snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Long)
        onMessageShown(current.id)
    }
}

/** The design's toast card: the surface fill inside a hairline ring, radius 12. */
@Composable
private fun HistorySnackbar(data: SnackbarData) {
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd)
                .border(1.dp, RepFlowColor.hairline, SnackbarShape),
        shape = SnackbarShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** `N workouts · newest first`; the order word is a button that flips the sort. */
@Composable
private fun CountLine(
    count: Int,
    sortOrder: HistorySortOrder,
    onSortOrderChanged: (HistorySortOrder) -> Unit,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val sortLabel =
        when (sortOrder) {
            HistorySortOrder.NEWEST_FIRST -> stringResource(R.string.history_filter_sort_newest)
            HistorySortOrder.OLDEST_FIRST -> stringResource(R.string.history_filter_sort_oldest)
        }
    val changeOrder = stringResource(R.string.history_sort_change_action)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CountLineTopGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CountLineGap),
    ) {
        Text(text = pluralStringResource(R.plurals.history_count, count, count), style = style, color = secondary)
        Text(text = stringResource(R.string.history_count_separator), style = style, color = secondary)
        Box(
            modifier =
                Modifier
                    .heightIn(min = HistoryChipTapTarget)
                    .clickable(role = Role.Button, onClickLabel = changeOrder) {
                        onSortOrderChanged(
                            if (sortOrder ==
                                HistorySortOrder.NEWEST_FIRST
                            ) {
                                HistorySortOrder.OLDEST_FIRST
                            } else {
                                HistorySortOrder.NEWEST_FIRST
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = sortLabel, style = style, color = secondary)
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<WorkoutSession>,
    uiState: HistoryUiState,
    onSessionClick: (WorkoutSessionId) -> Unit,
    listState: LazyListState,
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val sections = remember(sessions, zone) { monthSections(sessions, zone) }
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern(MONTH_PATTERN, locale) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = RepFlowSpacing.gapLg),
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "month-${section.month}-$index") {
                RepFlowSectionLabel(
                    text = monthFormatter.format(section.month),
                    modifier =
                        Modifier.padding(
                            start = RepFlowListRowDefaults.horizontalPadding,
                            top = if (index == 0) FirstMonthTopGap else MonthTopGap,
                            bottom = MonthBottomGap,
                        ),
                )
            }
            items(items = section.sessions, key = { it.id.value }) { session ->
                SessionRow(
                    session = session,
                    name = sessionName(session, uiState),
                    isPersonalBest = session.id in uiState.personalBestSessionIds,
                    zone = zone,
                    locale = locale,
                    onClick = { onSessionClick(session.id) },
                )
            }
        }
    }
}

/** A session's name: the plan it was started from, or `Untitled workout` as Home and the done screen call an ad-hoc one. */
@Composable
private fun sessionName(
    session: WorkoutSession,
    uiState: HistoryUiState,
): String =
    session.trainingPlanVersionId?.let { uiState.versionLabels[it]?.planName }
        ?: stringResource(R.string.home_untitled_workout)

/** `3a`'s row (`:1850-1865`): name and badges over `<when> · <time> · N sets · <volume>`, a caret, a divider. */
@Composable
private fun SessionRow(
    session: WorkoutSession,
    name: String,
    isPersonalBest: Boolean,
    zone: ZoneId,
    locale: Locale,
    onClick: () -> Unit,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = RepFlowListRowDefaults.tallMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(
                        horizontal = RepFlowListRowDefaults.horizontalPadding,
                        vertical = RepFlowListRowDefaults.verticalPadding,
                    ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowListRowDefaults.gap),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = RowNameFontSize),
                        color = if (session.isInvalidated) secondary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isPersonalBest && !session.isInvalidated) {
                        RepFlowStatusChip(
                            text = stringResource(R.string.history_row_badge_pr),
                            tone = RepFlowTagTone.UpNext,
                            icon = RepFlowIcons.medalFill,
                        )
                    }
                    if (session.isInvalidated) {
                        RepFlowStatusChip(
                            text = stringResource(R.string.history_row_badge_invalidated),
                            tone = RepFlowTagTone.Pending,
                            icon = RepFlowIcons.prohibit,
                        )
                    }
                }
                Text(
                    text = sessionMeta(session, zone, locale),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = secondary,
                    modifier = Modifier.padding(top = RepFlowListRowDefaults.metaGap),
                )
            }
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.trailingCaretAlpha),
                modifier = Modifier.size(RepFlowListRowDefaults.trailingCaretSize),
            )
        }
        HorizontalDivider(
            thickness = RepFlowListRowDefaults.dividerThickness,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
        )
    }
}

/** `Sat 8 Aug · 10:20 · 52 min · 18 sets · 6,720 kg` - the volume left out when no working set carries a load. */
@Composable
private fun sessionMeta(
    session: WorkoutSession,
    zone: ZoneId,
    locale: Locale,
): String {
    val sets = workingSetCount(session)
    val parts =
        listOfNotNull(
            DateTimeFormatter.ofPattern(ROW_WHEN_PATTERN, locale).format(session.startedAt.atZone(zone)),
            session.endedAt?.let { stringResource(R.string.history_duration_minutes, durationMinutes(session.startedAt, it)) },
            pluralStringResource(R.plurals.history_set_count, sets, sets),
            volumeKg(session)?.let { stringResource(R.string.history_volume_kg, NumberFormat.getIntegerInstance(locale).format(it)) },
        )
    return parts.joinToString(separator = META_SEPARATOR)
}

internal const val META_SEPARATOR = " · "

/** `August 2026`; the section label uppercases it. */
private const val MONTH_PATTERN = "LLLL yyyy"

/** `Sat 8 Aug · 10:20`. */
private const val ROW_WHEN_PATTERN = "EEE d MMM · HH:mm"

private val SnackbarShape = RoundedCornerShape(12.dp)
private val ChipsTopGap = 12.dp
private val CountLineTopGap = 4.dp
private val CountLineGap = 4.dp
private val FirstMonthTopGap = 4.dp
private val MonthTopGap = 16.dp
private val MonthBottomGap = 6.dp

/** `15.5/500` (`:1854`). */
private val RowNameFontSize = 15.5.sp
