package com.repflow.app.presentation.exercise.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSearchField
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * Stateless exercise library: state in, events out, no Hilt (see plan.md
 * section H) - Compose-tested directly via `createComposeRule`.
 *
 * `2c`'s composition (remediation-1 CP10), in CP3's frame: the sub-screen bar
 * (the library is reached from Settings, so it has a back arrow), a 48 search
 * field, the `Active` / `Archived` chips, a `N matches` count while searching,
 * flat 64dp rows (name over `type · rest · plan usage`, an archived row dimmed
 * with its `archived` badge, a 44dp overflow that opens a row-action sheet),
 * `Not here? Create "<query>"` under the results, and create pinned to the
 * bottom action bar (`D35`).
 *
 * State shape, the ViewModel's events and the message-queue snackbar handling
 * are unchanged; [onCreateFromQueryClick] is the one new event, carrying the
 * trimmed query to the editor's optional prefill.
 */
@Composable
fun ExerciseListScreen(
    uiState: ExerciseListUiState,
    onQueryChanged: (String) -> Unit,
    onFilterChanged: (ExerciseStatusFilter) -> Unit,
    onRetry: () -> Unit,
    onExerciseClick: (ExerciseId) -> Unit,
    onCreateClick: () -> Unit,
    onCreateFromQueryClick: (String) -> Unit,
    onArchiveClicked: (ExerciseId) -> Unit,
    onRestoreClicked: (ExerciseId) -> Unit,
    onUndoArchiveClicked: (ExerciseId) -> Unit,
    onMessageShown: (Long) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    ExerciseListMessages(uiState.messages, snackbarHostState, onUndoArchiveClicked, onMessageShown)

    RepFlowScreenScaffold(
        title = stringResource(R.string.exercise_list_title),
        modifier = modifier,
        onBack = onBackClick,
        bottomBar = { CreateExerciseBar(onCreateClick = onCreateClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) { ExerciseListSnackbar(it) } },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ExerciseSearchField(query = uiState.query, onQueryChanged = onQueryChanged)
            ExerciseFilterChips(filter = uiState.filter, onFilterChanged = onFilterChanged)
            val createFromQuery =
                uiState.query.trim().takeIf { it.isNotEmpty() && uiState.filter == ExerciseStatusFilter.ACTIVE }
            when (val content = uiState.content) {
                is ExerciseListContent.Loading -> {
                    RepFlowLoadingIndicator()
                }

                is ExerciseListContent.Content -> {
                    ExerciseRows(
                        items = content.items,
                        showMatchCount = uiState.query.isNotBlank(),
                        isArchivedFilter = uiState.filter == ExerciseStatusFilter.ARCHIVED,
                        createFromQuery = createFromQuery,
                        onExerciseClick = onExerciseClick,
                        onCreateFromQueryClick = onCreateFromQueryClick,
                        onArchiveClicked = onArchiveClicked,
                        onRestoreClicked = onRestoreClicked,
                    )
                }

                is ExerciseListContent.Empty -> {
                    if (content.reason == ExerciseListEmptyReason.NO_SEARCH_RESULTS && createFromQuery != null) {
                        CreateFromQueryFooter(
                            query = createFromQuery,
                            onCreateFromQueryClick = onCreateFromQueryClick,
                            modifier = Modifier.padding(top = RepFlowSpacing.screenPadding),
                        )
                    } else {
                        RepFlowEmptyState(
                            message = stringResource(exerciseListEmptyMessageRes(content.reason)),
                            icon = exerciseListEmptyIconRes(content.reason),
                        )
                    }
                }

                is ExerciseListContent.ObservationFailed -> {
                    RepFlowFailureState(
                        message = stringResource(R.string.exercise_list_observation_failed),
                        retryLabel = stringResource(R.string.exercise_list_retry),
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

/**
 * Shows the queued messages one at a time. A non-null actionLabel makes
 * Material3 default the duration to Indefinite, which never auto-dismisses and
 * (since messages are shown one at a time) blocks every later message too -
 * so the archive/Undo snackbar is given a finite duration explicitly
 * (Milestone 8, CP2).
 */
@Composable
private fun ExerciseListMessages(
    messages: List<ExerciseListMessage>,
    snackbarHostState: SnackbarHostState,
    onUndoArchiveClicked: (ExerciseId) -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val archivedText = stringResource(R.string.exercise_list_message_archived)
    val archivedUndoText = stringResource(R.string.exercise_list_message_archived_undo)
    val operationFailedText = stringResource(R.string.exercise_list_message_operation_failed)
    val message = messages.firstOrNull()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        val (text, actionLabel) =
            when (current) {
                is ExerciseListMessage.Archived -> archivedText to archivedUndoText
                is ExerciseListMessage.OperationFailed -> operationFailedText to null
            }
        val result =
            snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Long,
            )
        if (result == SnackbarResult.ActionPerformed && current is ExerciseListMessage.Archived) {
            onUndoArchiveClicked(current.exerciseId)
        }
        onMessageShown(current.id)
    }
}

/**
 * `2c`'s snackbar (`:2477-2481`): the surface fill inside a ring at radius 12,
 * an `arrow-counter-clockwise` before `Exercise archived.` and an accent
 * `Undo`. A message with no action (an operation failure) is the same card
 * with the words alone.
 */
@Composable
private fun ExerciseListSnackbar(data: SnackbarData) {
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
                    painter = painterResource(RepFlowIcons.arrowCounterClockwise),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(ExerciseFieldIconSize),
                )
            }
            Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * `6b`'s "one primary action per screen, pinned to a bottom bar" in place of
 * `2c`'s 60x60 FAB (`D35`). The button keeps the FAB's accessible name, which
 * is also its visible label, so the change is the container and not the
 * affordance's identity.
 */
@Composable
private fun CreateExerciseBar(onCreateClick: () -> Unit) {
    val label = stringResource(R.string.exercise_list_add_content_description)
    RepFlowBottomActionBar {
        RepFlowPrimaryButton(
            text = label,
            onClick = onCreateClick,
            leadingIcon = RepFlowIcons.plus,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

/**
 * `2c`'s search (`RepFlow.dc.html:2427-2431`): 48 tall, radius 10, the
 * surface fill behind a hairline, a leading magnifying glass and a trailing
 * clear that appears only once there is something to clear. Clearing routes
 * through the existing `onQueryChanged` callback.
 */
@Composable
private fun ExerciseSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    RepFlowSearchField(
        query = query,
        onQueryChange = onQueryChanged,
        placeholder = stringResource(R.string.exercise_list_search_hint),
        clearContentDescription = stringResource(R.string.exercise_list_search_clear_content_description),
    )
}

/**
 * `2c`'s filter chips (`:2432-2437`): 12.5 pills, padding 6/12, the selected
 * one accent-tinted. Each pill is drawn at the design's size inside a 44dp
 * tap target ([ExerciseFilterChipMinHeight]), so `6b`'s floor holds without
 * the row looking heavier than drawn. The chips take their order and labels
 * from [ExerciseListFilterOrder], the single place the order lives.
 */
@Composable
private fun ExerciseFilterChips(
    filter: ExerciseStatusFilter,
    onFilterChanged: (ExerciseStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = FilterRowTopGap).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        ExerciseListFilterOrder.forEach { option ->
            ExerciseFilterChip(
                label = stringResource(exerciseListFilterLabelRes(option)),
                selected = option == filter,
                onClick = { onFilterChanged(option) },
            )
        }
    }
}

@Composable
private fun ExerciseFilterChip(
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
                .heightIn(min = ExerciseFilterChipMinHeight)
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
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * `2c`'s `Not here? Create "press"` (`:2474`): under the results while a search
 * is active, and in place of them when nothing matches. The action opens the
 * editor with the query as its name (plan CP10 item 3); its tap target is
 * held at 44dp around the inline words.
 */
@Composable
internal fun CreateFromQueryFooter(
    query: String,
    onCreateFromQueryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.exercise_list_empty_no_search_results),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = FooterFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = ExerciseFilterChipMinHeight)
                    .clip(FooterActionShape)
                    .clickable(role = Role.Button) { onCreateFromQueryClick(query) }
                    .padding(horizontal = RepFlowSpacing.gapXs),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.exercise_list_create_from_query, query),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = FooterFontSize),
                color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
            )
        }
    }
}

/** The design's own row height: 64, inside its stated 56-68 range. */
internal val ExerciseRowMinHeight = 64.dp

/** Every chip and inline action's tap target: `6b`'s 44 floor. */
internal val ExerciseFilterChipMinHeight = 44.dp

/** The row's overflow `⋮`: `2c` draws 40x40, `6b`'s floor lifts it to 44 (`D36`). */
internal val ExerciseRowActionSize = 44.dp

/** In-field and in-row glyphs: search, clear, overflow. */
internal val ExerciseFieldIconSize = 18.dp

private val SnackbarShape = RoundedCornerShape(12.dp)
private val FilterRowTopGap = 4.dp
private val FooterFontSize = 13.sp
private val FooterActionShape = RoundedCornerShape(8.dp)
