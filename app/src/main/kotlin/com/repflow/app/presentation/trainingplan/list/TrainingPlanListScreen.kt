package com.repflow.app.presentation.trainingplan.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/**
 * Stateless training plan list screen: state in, events out, no Hilt.
 *
 * `5a`'s composition (remediation-1 CP11), in CP3's top-level frame: the
 * `Plans` title, the `Active` / `Archived` pills, one card per plan (name,
 * `N exercises · vN`, the archive date on an archived plan, `Archive` /
 * `Restore` on the card face, then `Start workout` and `Open`), `5a`'s empty
 * card, the footnote that archiving never touches completed workouts, the
 * archive snackbar as `5a`'s card, and `New plan` pinned to the bottom action
 * bar. No `Active plan` badge, no `Make active`, no days (`D4`): the list is a
 * flat set of plans, as before.
 *
 * The whole card still opens the plan, as the row did; `Open` is the same
 * action as an explicit button.
 */
@Composable
fun TrainingPlanListScreen(
    uiState: TrainingPlanListUiState,
    onRetry: () -> Unit,
    onPlanClick: (TrainingPlanId) -> Unit,
    onStartClick: (TrainingPlanId) -> Unit,
    onCreateClick: () -> Unit,
    onFilterChanged: (TrainingPlanStatusFilter) -> Unit,
    onArchiveClicked: (TrainingPlanId) -> Unit,
    onRestoreClicked: (TrainingPlanId) -> Unit,
    onUndoArchiveClicked: (TrainingPlanId) -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    TrainingPlanListMessages(uiState.messages, snackbarHostState, onUndoArchiveClicked, onMessageShown)

    RepFlowScreenScaffold(
        title = stringResource(R.string.training_plan_list_title),
        modifier = modifier,
        bottomBar = { NewPlanBar(onCreateClick = onCreateClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) { TrainingPlanListSnackbar(it) } },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            PlanFilterPills(filter = uiState.filter, onFilterChanged = onFilterChanged)
            val isArchivedFilter = uiState.filter == TrainingPlanStatusFilter.ARCHIVED
            when (val content = uiState.content) {
                is TrainingPlanListContent.Loading -> {
                    RepFlowLoadingIndicator()
                }

                is TrainingPlanListContent.Content -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = ListTopGap, bottom = RepFlowSpacing.gapLg),
                        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
                    ) {
                        items(items = content.items, key = { it.id.value }) { item ->
                            PlanCard(
                                item = item,
                                isArchivedFilter = isArchivedFilter,
                                onOpen = { onPlanClick(item.id) },
                                onStart = { onStartClick(item.id) },
                                onArchive = { onArchiveClicked(item.id) },
                                onRestore = { onRestoreClicked(item.id) },
                            )
                        }
                        item { ArchiveFootnote() }
                    }
                }

                is TrainingPlanListContent.Empty -> {
                    Column(
                        modifier = Modifier.padding(top = ListTopGap),
                        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
                    ) {
                        PlanEmptyCard(reason = content.reason)
                        ArchiveFootnote()
                    }
                }

                is TrainingPlanListContent.ObservationFailed -> {
                    RepFlowFailureState(
                        message = stringResource(R.string.training_plan_list_observation_failed),
                        retryLabel = stringResource(R.string.training_plan_list_retry),
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

/**
 * Shows the queued messages one at a time, each with a finite duration (an
 * action label would otherwise make Material3 default to Indefinite and block
 * every later message - Milestone 8, CP2).
 */
@Composable
private fun TrainingPlanListMessages(
    messages: List<TrainingPlanListMessage>,
    snackbarHostState: SnackbarHostState,
    onUndoArchiveClicked: (TrainingPlanId) -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val archivedText = stringResource(R.string.training_plan_list_message_archived)
    val archivedUndoText = stringResource(R.string.training_plan_list_message_archived_undo)
    val operationFailedText = stringResource(R.string.training_plan_list_message_operation_failed)
    val alreadyActiveText = stringResource(R.string.training_plan_list_message_already_active)
    val message = messages.firstOrNull()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val (text, actionLabel) =
            when (current) {
                is TrainingPlanListMessage.Archived -> archivedText to archivedUndoText
                is TrainingPlanListMessage.OperationFailed -> operationFailedText to null
                is TrainingPlanListMessage.WorkoutAlreadyActive -> alreadyActiveText to null
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
}

/**
 * `5a`'s toast (`RepFlow.dc.html:394-398`): the surface fill inside a ring at
 * radius 12, an `archive` glyph and an accent `Undo`. A message with no action
 * is the same card with the words alone.
 */
@Composable
private fun TrainingPlanListSnackbar(data: SnackbarData) {
    val scheme = MaterialTheme.colorScheme
    val accent = repFlowAccentOutlineColors(scheme).label
    val actionLabel = data.visuals.actionLabel
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd)
                .border(1.dp, RepFlowColor.hairline, SnackbarShape),
        shape = SnackbarShape,
        containerColor = scheme.surface,
        contentColor = scheme.onSurface,
        action =
            actionLabel?.let { label ->
                {
                    TextButton(onClick = data::performAction) {
                        Text(text = label, color = accent, style = MaterialTheme.typography.labelLarge)
                    }
                }
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            if (actionLabel != null) {
                Icon(
                    painter = painterResource(RepFlowIcons.archive),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(PlanGlyphSize),
                )
            }
            Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * `5a`'s `New plan` (`:403`): a 52-tall accent outline with a `plus`, pinned
 * under the list - `6b`'s bottom bar in place of the old FAB. On a tab whose
 * cards each carry their own primary (`Start workout`), the bar keeps the
 * design's quieter accent tier. The button keeps the FAB's accessible name,
 * which is now also its visible label.
 */
@Composable
private fun NewPlanBar(onCreateClick: () -> Unit) {
    val label = stringResource(R.string.training_plan_list_add_content_description)
    RepFlowBottomActionBar {
        RepFlowAccentOutlineButton(
            text = label,
            onClick = onCreateClick,
            leadingIcon = RepFlowIcons.plus,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = NewPlanButtonMinHeight)
                    .semantics { contentDescription = label },
        )
    }
}

/**
 * `5a`'s filter pills (`:350-355`): 12.5 text, padding 7/14, pill-shaped, the
 * selected one accent-tinted. Each is drawn at the design's size inside a 44dp
 * tap target, so `6b`'s floor holds without the pill looking heavier than
 * drawn.
 */
@Composable
private fun PlanFilterPills(
    filter: TrainingPlanStatusFilter,
    onFilterChanged: (TrainingPlanStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FilterRowTopGap).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        PlanFilterPill(
            label = stringResource(R.string.training_plan_list_filter_active),
            selected = filter == TrainingPlanStatusFilter.ACTIVE,
            onClick = { onFilterChanged(TrainingPlanStatusFilter.ACTIVE) },
        )
        PlanFilterPill(
            label = stringResource(R.string.training_plan_list_filter_archived),
            selected = filter == TrainingPlanStatusFilter.ARCHIVED,
            onClick = { onFilterChanged(TrainingPlanStatusFilter.ARCHIVED) },
        )
    }
}

@Composable
private fun PlanFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (selected) selectedColors.fill else Color.Transparent
    val border = if (selected) selectedColors.border else RepFlowColor.hairline
    val labelColor = if (selected) selectedColors.label else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            Modifier
                .heightIn(min = PlanTapTargetMinHeight)
                .clip(RepFlowShapes.pill)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor,
            modifier =
                Modifier
                    .background(fill, RepFlowShapes.pill)
                    .border(BorderStroke(1.dp, border), RepFlowShapes.pill)
                    .padding(horizontal = PillHorizontalPadding, vertical = PillVerticalPadding),
        )
    }
}

/** `6b`'s 44 floor: every pill, the card's archive/restore, and its two actions' minimum. */
internal val PlanTapTargetMinHeight = 44.dp

/** The snackbar, card and footnote glyphs. */
internal val PlanGlyphSize = 16.dp

private val SnackbarShape = RoundedCornerShape(12.dp)
private val NewPlanButtonMinHeight = 52.dp
private val FilterRowTopGap = 12.dp
private val ListTopGap = 14.dp
private val PillHorizontalPadding = 14.dp
private val PillVerticalPadding = 7.dp
