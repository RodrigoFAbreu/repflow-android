package com.repflow.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    onInvalidateClicked: (WorkoutSessionId) -> Unit,
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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

private fun formatDuration(session: WorkoutSession): String {
    val end = session.endedAt ?: return "?"
    val minutes = Duration.between(session.startedAt, end).toMinutes()
    return "${minutes}m"
}
