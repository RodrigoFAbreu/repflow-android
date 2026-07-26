package com.repflow.app.presentation.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.workout.WorkoutSession
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val detailDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** Read-only detail view of one completed [WorkoutSession]'s exercises and sets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    session: WorkoutSession,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(session.startedAt.atZone(ZoneId.systemDefault()).format(detailDateFormatter)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(stringResource(R.string.history_detail_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(session.exercises, key = { it.id.value }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.exerciseNameSnapshot) },
                    supportingContent = {
                        Text(stringResource(R.string.history_detail_set_count, exercise.sets.size))
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                for (set in exercise.sets) {
                    Text(
                        text =
                            stringResource(
                                R.string.history_detail_set_row,
                                set.order,
                                set.load?.toString().orEmpty(),
                                set.reps?.toString().orEmpty(),
                            ),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                    )
                    set.pain?.let { pain ->
                        Text(
                            text = stringResource(R.string.history_detail_set_pain, pain),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                        )
                    }
                    set.techniqueQuality?.let { techniqueQuality ->
                        Text(
                            text = stringResource(R.string.history_detail_set_technique_quality, techniqueQuality),
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
