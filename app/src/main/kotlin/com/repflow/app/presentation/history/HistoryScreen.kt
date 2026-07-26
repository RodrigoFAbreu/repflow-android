package com.repflow.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
private val filterDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Stateless history screen: a filterable/sortable completed-session list, tapping a row shows its exercises and sets read-only. */
@OptIn(ExperimentalMaterial3Api::class)
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
    val selectedSession = uiState.selectedSession
    if (selectedSession != null) {
        HistoryDetailScreen(session = selectedSession, onBackClick = onDetailDismissed, modifier = modifier)
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val invalidatedText = stringResource(R.string.history_message_invalidated)
    val operationFailedText = stringResource(R.string.history_message_operation_failed)

    val message = uiState.messages.firstOrNull()
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

    var sessionPendingInvalidation by remember { mutableStateOf<WorkoutSessionId?>(null) }
    val visibleSessions = uiState.visibleSessions

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HistoryFiltersBar(
                uiState = uiState,
                onExerciseFilterChanged = onExerciseFilterChanged,
                onPlanFilterChanged = onPlanFilterChanged,
                onStartDateChanged = onStartDateChanged,
                onEndDateChanged = onEndDateChanged,
                onShowInvalidatedChanged = onShowInvalidatedChanged,
                onSortOrderChanged = onSortOrderChanged,
            )
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                    }
                }

                uiState.sessions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.history_empty),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }

                visibleSessions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.history_empty_no_matches),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(visibleSessions, key = { it.id.value }) { session ->
                            ListItem(
                                headlineContent = {
                                    Text(sessionHeadline(session))
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.history_session_summary,
                                            session.exercises.size,
                                            formatDuration(session),
                                        ),
                                    )
                                },
                                trailingContent = {
                                    TextButton(onClick = { sessionPendingInvalidation = session.id }) {
                                        Text(stringResource(R.string.history_session_invalidate_action))
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = { onSessionClick(session.id) }),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    val pendingId = sessionPendingInvalidation
    if (pendingId != null) {
        AlertDialog(
            onDismissRequest = { sessionPendingInvalidation = null },
            title = { Text(stringResource(R.string.history_invalidate_dialog_title)) },
            text = { Text(stringResource(R.string.history_invalidate_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onInvalidateClicked(pendingId)
                        sessionPendingInvalidation = null
                    },
                ) {
                    Text(stringResource(R.string.history_invalidate_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionPendingInvalidation = null }) {
                    Text(stringResource(R.string.history_invalidate_dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun sessionHeadline(session: WorkoutSession): String {
    val date = session.startedAt.atZone(ZoneId.systemDefault()).format(dateFormatter)
    return if (session.isInvalidated) {
        stringResource(R.string.history_session_headline_invalidated, date)
    } else {
        date
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryFiltersBar(
    uiState: HistoryUiState,
    onExerciseFilterChanged: (ExerciseId?) -> Unit,
    onPlanFilterChanged: (HistoryPlanFilter) -> Unit,
    onStartDateChanged: (LocalDate?) -> Unit,
    onEndDateChanged: (LocalDate?) -> Unit,
    onShowInvalidatedChanged: (Boolean) -> Unit,
    onSortOrderChanged: (HistorySortOrder) -> Unit,
) {
    // A FlowRow (not a horizontally-scrolling Row) so every control stays reachable by a plain
    // tap on a phone-width screen - six filter controls don't all fit on one line, and an
    // instrumented test caught that a control scrolled off-screen in a horizontalScroll Row
    // couldn't actually be tapped on a real device.
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ExerciseFilterButton(uiState.filters.exerciseId, uiState.availableExerciseOptions, onExerciseFilterChanged)
        PlanFilterButton(uiState.filters.plan, uiState.availablePlanOptions, onPlanFilterChanged)
        DateFilterButton(
            date = uiState.filters.startDate,
            unsetLabelRes = R.string.history_filter_start_date_unset,
            onDateChanged = onStartDateChanged,
        )
        DateFilterButton(
            date = uiState.filters.endDate,
            unsetLabelRes = R.string.history_filter_end_date_unset,
            onDateChanged = onEndDateChanged,
        )
        SortOrderButton(uiState.filters.sortOrder, onSortOrderChanged)
        FilterChip(
            selected = uiState.filters.showInvalidated,
            onClick = { onShowInvalidatedChanged(!uiState.filters.showInvalidated) },
            label = { Text(stringResource(R.string.history_filter_show_invalidated)) },
        )
    }
}

@Composable
private fun ExerciseFilterButton(
    selectedId: ExerciseId?,
    options: List<HistoryExerciseFilterOption>,
    onExerciseFilterChanged: (ExerciseId?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = options.find { it.id == selectedId }?.name ?: stringResource(R.string.history_filter_exercise_all)
    Column {
        TextButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_exercise_all)) },
                onClick = {
                    expanded = false
                    onExerciseFilterChanged(null)
                },
            )
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onExerciseFilterChanged(option.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlanFilterButton(
    selected: HistoryPlanFilter,
    options: List<HistoryPlanFilter.Plan>,
    onPlanFilterChanged: (HistoryPlanFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label =
        when (selected) {
            HistoryPlanFilter.Any -> stringResource(R.string.history_filter_plan_any)
            HistoryPlanFilter.AdHocOnly -> stringResource(R.string.history_filter_plan_ad_hoc)
            is HistoryPlanFilter.Plan -> selected.planName
        }
    Column {
        TextButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_plan_any)) },
                onClick = {
                    expanded = false
                    onPlanFilterChanged(HistoryPlanFilter.Any)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.history_filter_plan_ad_hoc)) },
                onClick = {
                    expanded = false
                    onPlanFilterChanged(HistoryPlanFilter.AdHocOnly)
                },
            )
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(option.planName) },
                    onClick = {
                        expanded = false
                        onPlanFilterChanged(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun SortOrderButton(
    sortOrder: HistorySortOrder,
    onSortOrderChanged: (HistorySortOrder) -> Unit,
) {
    val label =
        when (sortOrder) {
            HistorySortOrder.NEWEST_FIRST -> stringResource(R.string.history_filter_sort_newest)
            HistorySortOrder.OLDEST_FIRST -> stringResource(R.string.history_filter_sort_oldest)
        }
    TextButton(
        onClick = {
            val next =
                if (sortOrder == HistorySortOrder.NEWEST_FIRST) HistorySortOrder.OLDEST_FIRST else HistorySortOrder.NEWEST_FIRST
            onSortOrderChanged(next)
        },
    ) {
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterButton(
    date: LocalDate?,
    unsetLabelRes: Int,
    onDateChanged: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val label = date?.format(filterDateFormatter) ?: stringResource(unsetLabelRes)
    TextButton(onClick = { showPicker = true }) { Text(label) }
    if (showPicker) {
        val initialMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = pickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onDateChanged(Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.history_date_picker_confirm))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onDateChanged(null)
                        showPicker = false
                    }) {
                        Text(stringResource(R.string.history_date_picker_clear))
                    }
                    TextButton(onClick = { showPicker = false }) {
                        Text(stringResource(R.string.history_date_picker_dismiss))
                    }
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatDuration(session: WorkoutSession): String {
    val end = session.endedAt ?: return "?"
    val minutes = Duration.between(session.startedAt, end).toMinutes()
    return "${minutes}m"
}
