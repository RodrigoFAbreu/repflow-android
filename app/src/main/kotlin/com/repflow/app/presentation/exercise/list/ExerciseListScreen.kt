package com.repflow.app.presentation.exercise.list

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowFailureState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowPillPicker
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/**
 * Stateless exercise list screen: state in, events out, no Hilt (see
 * plan.md section H) - Compose-tested directly via `createComposeRule`.
 *
 * The visual foundation reaches this screen through CP1's tokens and CP3's
 * primitives only: rows are [RepFlowCard]s, the filter row is the shared
 * [RepFlowPillPicker] (the one CP3 pill primitive that is interactive and
 * meets the 44dp tap-target floor - a 28dp [RepFlowStatusChip] would not),
 * and the three screen-level states are CP3's shared ones rather than the
 * private copies this file used to carry. Every glyph comes from CP2's
 * bounded local set; no icon library is reachable from here.
 *
 * State shape, callbacks and message-queue snackbar handling are untouched -
 * this checkpoint changes how the screen renders, never what it does.
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
        // A non-null actionLabel makes Material3 default duration to Indefinite,
        // which never auto-dismisses and (since messages are shown one at a time)
        // blocks every later message too - give the archive/Undo snackbar a finite
        // duration explicitly (Milestone 8, CP2).
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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercise_list_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = { CreateExerciseFab(onCreateClick = onCreateClick) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(top = RepFlowSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            ExerciseSearchField(query = uiState.query, onQueryChanged = onQueryChanged)
            ExerciseFilterRow(filter = uiState.filter, onFilterChanged = onFilterChanged)
            when (val content = uiState.content) {
                is ExerciseListContent.Loading -> {
                    RepFlowLoadingIndicator()
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
                    RepFlowEmptyState(message = stringResource(exerciseListEmptyMessageRes(content.reason)))
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
 * The design's solid-accent floating action button: 60x60 at
 * [RepFlowShapes.fab]'s 18dp radius, carrying CP2's bold plus (the design
 * draws this one plus bold and every other plus at regular weight).
 *
 * The accessible name stays on the button's own semantics block, as it was
 * before this checkpoint; the glyph inside is decorative, so a second
 * description on it would land on that same merged node.
 */
@Composable
private fun CreateExerciseFab(onCreateClick: () -> Unit) {
    val fabContentDescription = stringResource(R.string.exercise_list_add_content_description)
    FloatingActionButton(
        onClick = onCreateClick,
        modifier =
            Modifier
                .size(ExerciseFabSize)
                .semantics { contentDescription = fabContentDescription },
        shape = RepFlowShapes.fab,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.plusBold),
            contentDescription = null,
            modifier = Modifier.size(ExerciseFabIconSize),
        )
    }
}

/**
 * Search, with the design's two affordances: a leading magnifying glass and
 * a trailing clear button that appears only once there is something to
 * clear. Clearing routes through the existing `onQueryChanged` callback -
 * no new event, no state-shape change.
 */
@Composable
private fun ExerciseSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    val clearContentDescription = stringResource(R.string.exercise_list_search_clear_content_description)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text(stringResource(R.string.exercise_list_search_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        leadingIcon = {
            Icon(
                painter = painterResource(RepFlowIcons.magnifyingGlass),
                contentDescription = null,
                modifier = Modifier.size(ExerciseFieldIconSize),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChanged("") },
                    modifier = Modifier.semantics { contentDescription = clearContentDescription },
                ) {
                    Icon(
                        painter = painterResource(RepFlowIcons.xCircle),
                        contentDescription = null,
                        modifier = Modifier.size(ExerciseFieldIconSize),
                    )
                }
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RepFlowSpacing.screenPadding),
    )
}

/**
 * The active/archived filter, drawn as CP3's shared selectable pill row
 * behind the design's own funnel glyph.
 *
 * The picker takes an index, so [ExerciseListFilterOrder] is the single
 * place the row's order lives - labels and the selected index are both
 * derived from it rather than restated.
 */
@Composable
private fun ExerciseFilterRow(
    filter: ExerciseStatusFilter,
    onFilterChanged: (ExerciseStatusFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.funnel),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ExerciseFieldIconSize),
        )
        RepFlowPillPicker(
            options = ExerciseListFilterOrder.map { stringResource(exerciseListFilterLabelRes(it)) },
            selectedIndex = ExerciseListFilterOrder.indexOf(filter).takeIf { it >= 0 },
            onSelect = { onFilterChanged(ExerciseListFilterOrder[it]) },
            modifier = Modifier.weight(1f),
        )
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = RepFlowSpacing.screenPadding,
                end = RepFlowSpacing.screenPadding,
                // Clears the FAB, which floats over the end of the list.
                bottom = ExerciseFabSize + RepFlowSpacing.screenPadding * 2,
            ),
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
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

/**
 * One list row: the design's title/meta pair with a trailing kebab, on a
 * card at least [ExerciseRowMinHeight] tall (the design's own 64, inside
 * its 56-68 row range).
 *
 * The name is allowed a second line rather than being truncated at one -
 * the design calls out long exercise names as a case this screen handles,
 * and the height is a minimum, not a fixed size.
 */
@Composable
private fun ExerciseRow(
    item: ExerciseListItem,
    isArchivedFilter: Boolean,
    onClick: () -> Unit,
    onEditClicked: () -> Unit,
    onArchiveClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
) {
    RepFlowCard(
        modifier = Modifier.fillMaxWidth().heightIn(min = ExerciseRowMinHeight),
        onClick = onClick,
        contentPadding =
            PaddingValues(
                horizontal = RepFlowSpacing.cardPaddingMin,
                vertical = RepFlowSpacing.gapMd,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trackingTypeLabel(item.trackingType) + summarySuffix(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isArchivedFilter) {
                RepFlowStatusChip(
                    text = stringResource(R.string.exercise_list_row_archived_badge),
                    tone = RepFlowTagTone.Outline,
                    icon = RepFlowIcons.archive,
                )
            }
            ExerciseRowMenu(
                isArchivedFilter = isArchivedFilter,
                onEditClicked = onEditClicked,
                onArchiveClicked = onArchiveClicked,
                onRestoreClicked = onRestoreClicked,
            )
        }
    }
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
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = menuContentDescription },
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.dotsThreeVertical),
                contentDescription = null,
                modifier = Modifier.size(ExerciseFieldIconSize),
            )
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

/** The design's own row height: 64, inside its stated 56-68 range. */
internal val ExerciseRowMinHeight = 64.dp

/** The design's own floating action button: 60x60. */
internal val ExerciseFabSize = 60.dp

private val ExerciseFabIconSize = 24.dp

/** In-field and in-row glyphs: search, clear, funnel, kebab. */
private val ExerciseFieldIconSize = 20.dp
