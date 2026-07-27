package com.repflow.app.presentation.recovery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import java.time.format.DateTimeFormatter

private val historyDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Read-only recovery/futsal history list (Milestone 8, CP4) - nested under
 * the Recovery tab, so (unlike Recovery itself) it keeps an Up action per
 * the navigation-consistency rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryHistoryScreen(
    uiState: RecoveryHistoryUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recovery_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Text(stringResource(R.string.recovery_history_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.recoveryEntries.isEmpty() && uiState.futsalSessions.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.recovery_history_empty))
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (uiState.recoveryEntries.isNotEmpty()) {
                        item { Text(stringResource(R.string.recovery_history_recovery_section_title), modifier = Modifier.padding(16.dp)) }
                        items(uiState.recoveryEntries, key = { "recovery-${it.id.value}" }) { entry ->
                            RecoveryEntryRow(entry)
                        }
                    }
                    if (uiState.futsalSessions.isNotEmpty()) {
                        item { Text(stringResource(R.string.recovery_history_futsal_section_title), modifier = Modifier.padding(16.dp)) }
                        items(uiState.futsalSessions, key = { "futsal-${it.id.value}" }) { session ->
                            FutsalSessionRow(session)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryEntryRow(entry: RecoveryEntry) {
    ListItem(
        headlineContent = { Text(entry.date.format(historyDateFormatter)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.recovery_history_recovery_row,
                    entry.sleepQuality,
                    entry.energy,
                    entry.painWhileWalking,
                ),
            )
        },
    )
}

@Composable
private fun FutsalSessionRow(session: FutsalSession) {
    ListItem(
        headlineContent = { Text(session.date.format(historyDateFormatter)) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.recovery_history_futsal_row,
                    session.durationMinutes,
                    session.sessionRpe.toString(),
                    session.load.toString(),
                ),
            )
        },
    )
}
