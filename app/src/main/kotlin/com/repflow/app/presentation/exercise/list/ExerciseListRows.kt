package com.repflow.app.presentation.exercise.list

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowListRow
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The library's rows and their action sheet (remediation-1 CP10, `2c`
 * `:2440-2474`), split from `ExerciseListScreen.kt` to keep each file under
 * Detekt's per-file function count.
 */

@Composable
internal fun ExerciseRows(
    items: List<ExerciseListItem>,
    showMatchCount: Boolean,
    isArchivedFilter: Boolean,
    createFromQuery: String?,
    onExerciseClick: (ExerciseId) -> Unit,
    onCreateFromQueryClick: (String) -> Unit,
    onArchiveClicked: (ExerciseId) -> Unit,
    onRestoreClicked: (ExerciseId) -> Unit,
) {
    // The id of the row whose action sheet is up; a plain string so it
    // survives recreation, resolved against the current rows on every pass.
    var sheetForId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (showMatchCount) {
            item(key = "match-count") {
                RepFlowSectionLabel(
                    text = pluralStringResource(R.plurals.exercise_list_match_count, items.size, items.size),
                    modifier = Modifier.padding(top = RepFlowSpacing.gapSm, bottom = MatchCountBottomGap),
                )
            }
        }
        items(items = items, key = { it.id.value }) { item ->
            ExerciseRow(
                item = item,
                isArchived = isArchivedFilter,
                onClick = { onExerciseClick(item.id) },
                onMoreClick = { sheetForId = item.id.value },
            )
        }
        if (createFromQuery != null) {
            item(key = "create-from-query") {
                CreateFromQueryFooter(
                    query = createFromQuery,
                    onCreateFromQueryClick = onCreateFromQueryClick,
                    modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
                )
            }
        }
    }
    val sheetItem = items.firstOrNull { it.id.value == sheetForId }
    if (sheetItem != null) {
        ExerciseRowSheet(
            item = sheetItem,
            isArchived = isArchivedFilter,
            onDismissRequest = { sheetForId = null },
            onEditClicked = {
                sheetForId = null
                onExerciseClick(sheetItem.id)
            },
            onArchiveClicked = {
                sheetForId = null
                onArchiveClicked(sheetItem.id)
            },
            onRestoreClicked = {
                sheetForId = null
                onRestoreClicked(sheetItem.id)
            },
        )
    }
}

/**
 * One row (`2c`): at least [ExerciseRowMinHeight] tall, the name at 15/500 -
 * allowed a second line, since the design calls out long names - over the
 * 12.5 meta, then the overflow. An archived row is drawn at 60% with its
 * `archived` badge beside the name, the word carrying the state (`6b`: never
 * colour alone).
 */
@Composable
private fun ExerciseRow(
    item: ExerciseListItem,
    isArchived: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val moreContentDescription = stringResource(R.string.exercise_list_row_menu_content_description)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ExerciseRowMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .alpha(if (isArchived) ARCHIVED_ROW_ALPHA else 1f)
                    .padding(vertical = RepFlowSpacing.gapLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isArchived) {
                        RepFlowStatusChip(
                            text = stringResource(R.string.exercise_list_row_archived_badge),
                            tone = RepFlowTagTone.Outline,
                            icon = RepFlowIcons.archive,
                        )
                    }
                }
                Text(
                    text = exerciseRowMeta(item),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(ExerciseRowActionSize)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onMoreClick)
                        .semantics { contentDescription = moreContentDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.dotsThreeVertical),
                    contentDescription = null,
                    tint = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    modifier = Modifier.size(ExerciseFieldIconSize),
                )
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = ROW_DIVIDER_ALPHA),
        )
    }
}

/**
 * The row's actions as a sheet (`6b`: "sheets for choices"), replacing the
 * `DropdownMenu`: edit, then archive - or restore, on the archived filter.
 * Archive needs no confirmation: it is undoable from the snackbar.
 */
@Composable
private fun ExerciseRowSheet(
    item: ExerciseListItem,
    isArchived: Boolean,
    onDismissRequest: () -> Unit,
    onEditClicked: () -> Unit,
    onArchiveClicked: () -> Unit,
    onRestoreClicked: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismissRequest, title = item.name) {
        Column(modifier = Modifier.padding(horizontal = RepFlowSpacing.screenPadding)) {
            SheetActionRow(
                title = stringResource(R.string.exercise_list_row_menu_edit),
                icon = RepFlowIcons.pencilSimple,
                onClick = onEditClicked,
            )
            if (isArchived) {
                SheetActionRow(
                    title = stringResource(R.string.exercise_list_row_menu_restore),
                    icon = RepFlowIcons.arrowCounterClockwise,
                    onClick = onRestoreClicked,
                )
            } else {
                SheetActionRow(
                    title = stringResource(R.string.exercise_list_row_menu_archive),
                    icon = RepFlowIcons.archive,
                    onClick = onArchiveClicked,
                )
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    title: String,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
) {
    RepFlowListRow(
        title = title,
        onClick = onClick,
        showDivider = false,
        leading = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(SheetIconSize),
            )
        },
        trailing = {},
    )
}

/**
 * `2c`'s meta, less the muscle group the domain does not have (`D8`):
 * `<tracking type> · rest m:ss · in N plans` (or `not in any plan`). The rest
 * part is left out when the exercise sets no default rest.
 */
@Composable
private fun exerciseRowMeta(item: ExerciseListItem): String {
    val rest =
        item.defaultRestSeconds?.let { seconds ->
            stringResource(
                R.string.exercise_list_meta_rest,
                stringResource(R.string.exercise_rest_clock, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE),
            )
        }
    val usage =
        if (item.planUsageCount > 0) {
            pluralStringResource(R.plurals.exercise_list_meta_plan_usage, item.planUsageCount, item.planUsageCount)
        } else {
            stringResource(R.string.exercise_list_meta_no_plan)
        }
    return listOfNotNull(trackingTypeLabel(item.trackingType), rest, usage).joinToString(separator = " · ")
}

@Composable
private fun trackingTypeLabel(trackingType: ExerciseTrackingType): String =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.exercise_tracking_type_weight_and_reps)
        ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.exercise_tracking_type_reps_only)
        ExerciseTrackingType.DURATION -> stringResource(R.string.exercise_tracking_type_duration)
    }

private const val SECONDS_PER_MINUTE = 60L

/** `2c`'s archived row: `opacity:.6`. */
private const val ARCHIVED_ROW_ALPHA = 0.6f

/** `2c`'s row divider, `rgba(233,233,237,.07)`. */
private const val ROW_DIVIDER_ALPHA = 0.07f

private val MatchCountBottomGap = 2.dp
private val SheetIconSize = 20.dp
