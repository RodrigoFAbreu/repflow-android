package com.repflow.app.presentation.exercise.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType

/**
 * Stateless exercise list screen: state in, events out, no Hilt (see
 * plan.md section H) - Compose-tested directly via `createComposeRule`.
 *
 * The FAB uses a plain "+" glyph rather than a vector icon - neither
 * `material-icons-core` nor `material-icons-extended` is a dependency of
 * this project (see plan.md section L, "not added").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListScreen(
    uiState: ExerciseListUiState,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (ExerciseStatusFilter) -> Unit,
    onRetry: () -> Unit,
    onExerciseClick: (ExerciseId) -> Unit,
    onCreateClick: () -> Unit,
    onArchiveClicked: (ExerciseId) -> Unit,
    onRestoreClicked: (ExerciseId) -> Unit,
    onUndoArchiveClicked: (ExerciseId) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabContentDescription = stringResource(R.string.exercise_list_add_content_description)
    val snackbarHostState = remember { SnackbarHostState() }
    val archivedText = stringResource(R.string.exercise_list_message_archived)
    val archivedUndoText = stringResource(R.string.exercise_list_message_archived_undo)
    val operationFailedText = stringResource(R.string.exercise_list_message_operation_failed)

    val message = uiState.messages.firstOrNull()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val (text, actionLabel) =
            when (current) {
                is ExerciseListMessage.Archived -> archivedText to archivedUndoText
                is ExerciseListMessage.OperationFailed -> operationFailedText to null
            }
        val result = snackbarHostState.showSnackbar(message = text, actionLabel = actionLabel)
        if (result == SnackbarResult.ActionPerformed && current is ExerciseListMessage.Archived) {
            onUndoArchiveClicked(current.exerciseId)
        }
        onMessageShown(current.id)
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.exercise_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                modifier =
                    Modifier.semantics {
                        contentDescription = fabContentDescription
                    },
            ) {
                Text(
                    text = "+",
                    modifier = Modifier.padding(4.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChanged,
                placeholder = { Text(stringResource(R.string.exercise_list_search_hint)) },
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = uiState.filter == ExerciseStatusFilter.ACTIVE,
                    onClick = { onFilterChanged(ExerciseStatusFilter.ACTIVE) },
                    label = { Text(stringResource(R.string.exercise_list_filter_active)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.filter == ExerciseStatusFilter.ARCHIVED,
                    onClick = { onFilterChanged(ExerciseStatusFilter.ARCHIVED) },
                    label = { Text(stringResource(R.string.exercise_list_filter_archived)) },
                )
            }
            when (val content = uiState.content) {
                is ExerciseListContent.Loading -> {
                    LoadingIndicator()
                }

                is ExerciseListContent.Content -> {
                    ExerciseRows(
                        items = content.items,
                        isArchivedFilter = uiState.filter == ExerciseStatusFilter.ARCHIVED,
                        onExerciseClick = onExerciseClick,
                        onArchiveClicked = onArchiveClicked,
                        onRestoreClicked = onRestoreClicked,
                    )
                }

                is ExerciseListContent.Empty -> {
                    EmptyState(reason = content.reason)
                }

                is ExerciseListContent.ObservationFailed -> {
                    FailureState(onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ExerciseRows(
    items: List<ExerciseListItem>,
    isArchivedFilter: Boolean,
    onExerciseClick: (ExerciseId) -> Unit,
    onArchiveClicked: (ExerciseId) -> Unit,
    onRestoreClicked: (ExerciseId) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id.value }) { item ->
            ExerciseRow(
                item = item,
                isArchivedFilter = isArchivedFilter,
                onClick = { onExerciseClick(item.id) },
                onEditClicked = { onExerciseClick(item.id) },
                onArchiveClicked = { onArchiveClicked(item.id) },
                onRestoreClicked = { onRestoreClicked(item.id) },
            )
        }
    }
}

@Composable
private fun ExerciseRow(
    item: ExerciseListItem,
    isArchivedFilter: Boolean,
    onClick: () -> Unit,
    onEditClicked: () -> Unit,
    onArchiveClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(item.name) },
        supportingContent = { Text(trackingTypeLabel(item.trackingType) + summarySuffix(item)) },
        trailingContent = {
            ExerciseRowMenu(
                isArchivedFilter = isArchivedFilter,
                onEditClicked = onEditClicked,
                onArchiveClicked = onArchiveClicked,
                onRestoreClicked = onRestoreClicked,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ExerciseRowMenu(
    isArchivedFilter: Boolean,
    onEditClicked: () -> Unit,
    onArchiveClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuContentDescription = stringResource(R.string.exercise_list_row_menu_content_description)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = menuContentDescription },
        ) {
            Text("⋮")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.exercise_list_row_menu_edit)) },
                onClick = {
                    expanded = false
                    onEditClicked()
                },
            )
            if (isArchivedFilter) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_list_row_menu_restore)) },
                    onClick = {
                        expanded = false
                        onRestoreClicked()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.exercise_list_row_menu_archive)) },
                    onClick = {
                        expanded = false
                        onArchiveClicked()
                    },
                )
            }
        }
    }
}

@Composable
private fun trackingTypeLabel(trackingType: ExerciseTrackingType): String =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.exercise_tracking_type_weight_and_reps)
        ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.exercise_tracking_type_reps_only)
        ExerciseTrackingType.DURATION -> stringResource(R.string.exercise_tracking_type_duration)
    }

@Composable
private fun summarySuffix(item: ExerciseListItem): String {
    val restSummary =
        item.defaultRestSeconds?.let { stringResource(R.string.exercise_default_rest_seconds_summary, it) }
    val loadSummary =
        item.defaultLoadIncrementGrams?.let {
            stringResource(R.string.exercise_default_load_increment_summary, it)
        }
    val parts = listOfNotNull(restSummary, loadSummary)
    return if (parts.isEmpty()) "" else parts.joinToString(separator = " · ", prefix = " · ")
}

@Composable
private fun EmptyState(reason: ExerciseListEmptyReason) {
    val textRes =
        when (reason) {
            ExerciseListEmptyReason.NO_EXERCISES -> R.string.exercise_list_empty_no_exercises
            ExerciseListEmptyReason.NO_SEARCH_RESULTS -> R.string.exercise_list_empty_no_search_results
            ExerciseListEmptyReason.NO_ARCHIVED -> R.string.exercise_list_empty_no_archived
        }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(textRes))
    }
}

@Composable
private fun FailureState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.exercise_list_observation_failed))
        }
        Button(onClick = onRetry) {
            Text(stringResource(R.string.exercise_list_retry))
        }
    }
}
