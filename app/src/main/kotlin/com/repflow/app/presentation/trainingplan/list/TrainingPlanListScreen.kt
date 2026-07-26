package com.repflow.app.presentation.trainingplan.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanId

/**
 * Stateless training plan list screen: state in, events out, no Hilt (see
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreen] for the
 * same shape). Mirrors it, minus the search/filter row - plans have neither
 * in M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingPlanListScreen(
    uiState: TrainingPlanListUiState,
    onRetry: () -> Unit,
    onPlanClick: (TrainingPlanId) -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fabContentDescription = stringResource(R.string.training_plan_list_add_content_description)
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.training_plan_list_title)) }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateClick,
                modifier = Modifier.semantics { contentDescription = fabContentDescription },
            ) {
                Text(text = "+", modifier = Modifier.padding(4.dp))
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val content = uiState.content) {
                is TrainingPlanListContent.Loading -> LoadingIndicator()
                is TrainingPlanListContent.Content -> PlanRows(content.items, onPlanClick)
                is TrainingPlanListContent.Empty -> EmptyState()
                is TrainingPlanListContent.ObservationFailed -> FailureState(onRetry)
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
    onPlanClick: (TrainingPlanId) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = items, key = { it.id.value }) { item ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = {
                    Text(stringResource(R.string.training_plan_list_exercise_count, item.plannedExerciseCount))
                },
                modifier = Modifier.clickable { onPlanClick(item.id) },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.training_plan_list_empty))
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
