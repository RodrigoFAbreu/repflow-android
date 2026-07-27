package com.repflow.app.presentation.trainingplan.list

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stateless training plan list screen: state in, events out, no Hilt. Mirrors
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreen]'s
 * active/archived filter chips, row menu, and archive/Undo snackbar
 * (Milestone 8, CP12) - minus the search field, which plans still don't
 * have.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanListScreen(
    uiState: TrainingPlanListUiState,
    onRetry: () -> Unit,
    onPlanClick: (TrainingPlanId) -> Unit,
    onCreateClick: () -> Unit,
    onFilterChanged: (TrainingPlanStatusFilter) -> Unit,
    onArchiveClicked: (TrainingPlanId) -> Unit,
    onRestoreClicked: (TrainingPlanId) -> Unit,
    onUndoArchiveClicked: (TrainingPlanId) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabContentDescription = stringResource(R.string.training_plan_list_add_content_description)
    val snackbarHostState = remember { SnackbarHostState() }
    val archivedText = stringResource(R.string.training_plan_list_message_archived)
    val archivedUndoText = stringResource(R.string.training_plan_list_message_archived_undo)
    val operationFailedText = stringResource(R.string.training_plan_list_message_operation_failed)

    val message = uiState.messages.firstOrNull()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val (text, actionLabel) =
            when (current) {
                is TrainingPlanListMessage.Archived -> archivedText to archivedUndoText
                is TrainingPlanListMessage.OperationFailed -> operationFailedText to null
            }
        val result =
            snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed && current is TrainingPlanListMessage.Archived) {
            onUndoArchiveClicked(current.planId)
        }
        onMessageShown(current.id)
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.training_plan_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier.semantics { contentDescription = fabContentDescription },
            ) {
                Text(text = "+", modifier = Modifier.padding(4.dp))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = uiState.filter == TrainingPlanStatusFilter.ACTIVE,
                    onClick = { onFilterChanged(TrainingPlanStatusFilter.ACTIVE) },
                    label = { Text(stringResource(R.string.training_plan_list_filter_active)) },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = uiState.filter == TrainingPlanStatusFilter.ARCHIVED,
                    onClick = { onFilterChanged(TrainingPlanStatusFilter.ARCHIVED) },
                    label = { Text(stringResource(R.string.training_plan_list_filter_archived)) },
                )
            }
            when (val content = uiState.content) {
                is TrainingPlanListContent.Loading -> {
                    LoadingIndicator()
                }

                is TrainingPlanListContent.Content -> {
                    PlanRows(
                        items = content.items,
                        isArchivedFilter = uiState.filter == TrainingPlanStatusFilter.ARCHIVED,
                        onPlanClick = onPlanClick,
                        onArchiveClicked = onArchiveClicked,
                        onRestoreClicked = onRestoreClicked,
                    )
                }

                is TrainingPlanListContent.Empty -> {
                    EmptyState(content.reason)
                }

                is TrainingPlanListContent.ObservationFailed -> {
                    FailureState(onRetry)
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
private fun PlanRows(
    items: List<TrainingPlanListItem>,
    isArchivedFilter: Boolean,
    onPlanClick: (TrainingPlanId) -> Unit,
    onArchiveClicked: (TrainingPlanId) -> Unit,
    onRestoreClicked: (TrainingPlanId) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id.value }) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = {
                    Text(stringResource(R.string.training_plan_list_exercise_count, item.plannedExerciseCount))
                },
                trailingContent = {
                    PlanRowMenu(
                        isArchivedFilter = isArchivedFilter,
                        onEditClicked = { onPlanClick(item.id) },
                        onArchiveClicked = { onArchiveClicked(item.id) },
                        onRestoreClicked = { onRestoreClicked(item.id) },
                    )
                },
                modifier = Modifier.clickable { onPlanClick(item.id) },
            )
        }
    }
}

@Composable
private fun PlanRowMenu(
    isArchivedFilter: Boolean,
    onEditClicked: () -> Unit,
    onArchiveClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuContentDescription = stringResource(R.string.training_plan_list_row_menu_content_description)
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = menuContentDescription },
        ) {
            Text("⋮")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.training_plan_list_row_menu_edit)) },
                onClick = {
                    expanded = false
                    onEditClicked()
                },
            )
            if (isArchivedFilter) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_plan_list_row_menu_restore)) },
                    onClick = {
                        expanded = false
                        onRestoreClicked()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.training_plan_list_row_menu_archive)) },
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
private fun EmptyState(reason: TrainingPlanListEmptyReason) {
    val textRes =
        when (reason) {
            TrainingPlanListEmptyReason.NO_PLANS -> R.string.training_plan_list_empty
            TrainingPlanListEmptyReason.NO_ARCHIVED -> R.string.training_plan_list_empty_no_archived
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
            Text(stringResource(R.string.training_plan_list_observation_failed))
        }
        Button(onClick = onRetry) {
            Text(stringResource(R.string.training_plan_list_retry))
        }
    }
}
