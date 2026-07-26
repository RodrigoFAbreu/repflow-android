package com.repflow.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** Stateless history screen: a completed-session list, tapping a row shows its exercises and sets read-only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    onSessionClick: (WorkoutSessionId) -> Unit,
    onDetailDismissed: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedSession = uiState.selectedSession
    if (selectedSession != null) {
        HistoryDetailScreen(session = selectedSession, onBackClick = onDetailDismissed, modifier = modifier)
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(stringResource(R.string.history_back_content_description))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
                }
            }

            uiState.sessions.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        text = stringResource(R.string.history_empty),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(uiState.sessions, key = { it.id.value }) { session ->
                        ListItem(
                            headlineContent = { Text(session.startedAt.atZone(ZoneId.systemDefault()).format(dateFormatter)) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        R.string.history_session_summary,
                                        session.exercises.size,
                                        formatDuration(session),
                                    ),
                                )
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

private fun formatDuration(session: WorkoutSession): String {
    val end = session.endedAt ?: return "?"
    val minutes = Duration.between(session.startedAt, end).toMinutes()
    return "${minutes}m"
}
