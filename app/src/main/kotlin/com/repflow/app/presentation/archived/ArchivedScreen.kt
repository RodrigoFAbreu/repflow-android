package com.repflow.app.presentation.archived

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.exercise.list.ExerciseListSnackbar
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Archived screen (remediation-1-remediation-1 CP7, Q4), reached only from
 * Settings' `Archived exercises and plans`; drawn from design turn 7 `7c` N1
 * (`5c` draws the Settings row, not its destination). `Exercises`, then
 * `Plans`: each row is the name over `Archived <date>` with an outlined 44dp
 * `Restore`. A restored row leaves at once; a section with nothing archived
 * says so in one inline line, and with both empty a single empty state
 * replaces the lists.
 */
@Composable
fun ArchivedScreen(
    uiState: ArchivedUiState,
    onRestoreClicked: (ArchivedItem) -> Unit,
    onUndoRestoreClicked: (ArchivedTarget) -> Unit,
    onMessageShown: (Long) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ArchivedMessages(uiState.messages, snackbarHostState, onUndoRestoreClicked, onMessageShown)

    RepFlowScreenScaffold(
        title = stringResource(R.string.archived_title),
        onBack = onBack,
        backContentDescription = stringResource(R.string.archived_back_content_description),
        snackbarHost = { SnackbarHost(snackbarHostState) { ExerciseListSnackbar(it) } },
        modifier = modifier,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val content = uiState.content) {
                is ArchivedContent.Loading -> {
                    RepFlowLoadingIndicator()
                }

                is ArchivedContent.ObservationFailed -> {
                    RepFlowFailureState(
                        message = stringResource(R.string.archived_observation_failed),
                        retryLabel = stringResource(R.string.archived_retry),
                        onRetry = onRetry,
                    )
                }

                is ArchivedContent.Loaded -> {
                    if (content.exercises.isEmpty() && content.plans.isEmpty()) {
                        RepFlowEmptyState(
                            message = stringResource(R.string.archived_empty_both),
                            icon = RepFlowIcons.archive,
                        )
                    } else {
                        ArchivedLists(content, onRestoreClicked)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedLists(
    content: ArchivedContent.Loaded,
    onRestoreClicked: (ArchivedItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = RepFlowSpacing.screenPadding),
    ) {
        section(R.string.archived_section_exercises, R.string.archived_empty_exercises, content.exercises, onRestoreClicked)
        section(R.string.archived_section_plans, R.string.archived_empty_plans, content.plans, onRestoreClicked)
    }
}

private fun LazyListScope.section(
    labelRes: Int,
    emptyRes: Int,
    items: List<ArchivedItem>,
    onRestoreClicked: (ArchivedItem) -> Unit,
) {
    item(key = "label-$labelRes") {
        RepFlowSectionLabel(
            text = stringResource(labelRes),
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
    }
    if (items.isEmpty()) {
        item(key = "empty-$labelRes") {
            Text(
                text = stringResource(emptyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    } else {
        items(items, key = { item -> item.target.toString() }) { item ->
            ArchivedRow(item, onRestoreClicked = { onRestoreClicked(item) })
        }
    }
}

@Composable
private fun ArchivedRow(
    item: ArchivedItem,
    onRestoreClicked: () -> Unit,
) {
    val restoreDescription = stringResource(R.string.archived_restore_content_description, item.name)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, style = MaterialTheme.typography.bodyLarge)
                item.archivedAt?.let { archivedAt ->
                    val date = archivedAt.atZone(ZoneId.systemDefault()).toLocalDate().format(ArchivedDateFormatter)
                    Text(
                        text = stringResource(R.string.archived_row_meta, date),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = MetaFontSize),
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.archived_restore_action),
                onClick = onRestoreClicked,
                modifier = Modifier.semantics { contentDescription = restoreDescription },
            )
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = DIVIDER_ALPHA))
    }
}

/** Shows the queued messages one at a time; `<name> restored` carries `Undo`, which archives it again. */
@Composable
private fun ArchivedMessages(
    messages: List<ArchivedMessage>,
    snackbarHostState: SnackbarHostState,
    onUndoRestoreClicked: (ArchivedTarget) -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val undoText = stringResource(R.string.archived_message_restored_undo)
    val failedText = stringResource(R.string.archived_message_operation_failed)
    val message = messages.firstOrNull()
    val restoredText = (message as? ArchivedMessage.Restored)?.let { stringResource(R.string.archived_message_restored, it.name) }
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val (text, actionLabel) =
            when (current) {
                is ArchivedMessage.Restored -> restoredText.orEmpty() to undoText
                is ArchivedMessage.OperationFailed -> failedText to null
            }
        // A finite duration: an action makes Material default to Indefinite, which would block later messages.
        val result = snackbarHostState.showSnackbar(message = text, actionLabel = actionLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed && current is ArchivedMessage.Restored) {
            onUndoRestoreClicked(current.target)
        }
        onMessageShown(current.id)
    }
}

private val ArchivedDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val RowMinHeight = 56.dp
private val MetaFontSize = 12.5.sp
private const val DIVIDER_ALPHA = 0.09f
