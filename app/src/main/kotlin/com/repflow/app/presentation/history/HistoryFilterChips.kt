package com.repflow.app.presentation.history

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowListRow
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/*
 * `3a`'s filter chips (remediation-1 CP12, `RepFlow.dc.html:1838-1844`), over
 * the existing `HistoryFilters`: the plan, `Show invalidated`, the two ends of
 * the date range, and the exercise. Each is drawn at the design's size (12.5
 * text, padding 7/13, 36 tall, a pill) inside a 44dp tap target; a chip whose
 * filter is set is accent-tinted. The plan, exercise and date choices open as
 * sheets (`6b`: "sheets for choices"), replacing the old `DropdownMenu`s and
 * the date-picker dialog.
 *
 * A `FlowRow`, not a horizontally scrolling row: every chip stays reachable by
 * a plain tap on a phone-width screen (Milestone 8 found a chip scrolled off a
 * `horizontalScroll` row could not be tapped on a real device).
 */

private val filterDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Which chooser sheet is open, if any. */
private enum class HistoryFilterSheet { PLAN, EXERCISE, START_DATE, END_DATE }

@Composable
internal fun HistoryFilterChips(
    uiState: HistoryUiState,
    onExerciseFilterChanged: (ExerciseId?) -> Unit,
    onPlanFilterChanged: (HistoryPlanFilter) -> Unit,
    onStartDateChanged: (LocalDate?) -> Unit,
    onEndDateChanged: (LocalDate?) -> Unit,
    onShowInvalidatedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var openSheet by remember { mutableStateOf<HistoryFilterSheet?>(null) }
    val filters = uiState.filters
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        HistoryFilterChip(
            label = planChipLabel(filters.plan),
            icon = RepFlowIcons.listChecks,
            selected = filters.plan != HistoryPlanFilter.Any,
            onClick = { openSheet = HistoryFilterSheet.PLAN },
        )
        HistoryToggleChip(
            label = stringResource(R.string.history_filter_show_invalidated),
            icon = RepFlowIcons.prohibit,
            checked = filters.showInvalidated,
            onCheckedChange = onShowInvalidatedChanged,
        )
        HistoryFilterChip(
            label =
                filters.startDate?.let { stringResource(R.string.history_filter_start_date_set, it.format(filterDateFormatter)) }
                    ?: stringResource(R.string.history_filter_start_date_unset),
            icon = RepFlowIcons.calendarBlank,
            selected = filters.startDate != null,
            onClick = { openSheet = HistoryFilterSheet.START_DATE },
        )
        HistoryFilterChip(
            label =
                filters.endDate?.let { stringResource(R.string.history_filter_end_date_set, it.format(filterDateFormatter)) }
                    ?: stringResource(R.string.history_filter_end_date_unset),
            icon = RepFlowIcons.calendarBlank,
            selected = filters.endDate != null,
            onClick = { openSheet = HistoryFilterSheet.END_DATE },
        )
        HistoryFilterChip(
            label =
                uiState.availableExerciseOptions.find { it.id == filters.exerciseId }?.name
                    ?: stringResource(R.string.history_filter_exercise_all),
            icon = RepFlowIcons.barbell,
            selected = filters.exerciseId != null,
            onClick = { openSheet = HistoryFilterSheet.EXERCISE },
        )
    }

    val close = { openSheet = null }
    when (openSheet) {
        HistoryFilterSheet.PLAN -> {
            PlanFilterSheet(
                selected = filters.plan,
                options = uiState.availablePlanOptions,
                onSelect = { plan ->
                    close()
                    onPlanFilterChanged(plan)
                },
                onDismiss = close,
            )
        }

        HistoryFilterSheet.EXERCISE -> {
            ExerciseFilterSheet(
                selectedId = filters.exerciseId,
                options = uiState.availableExerciseOptions,
                onSelect = { id ->
                    close()
                    onExerciseFilterChanged(id)
                },
                onDismiss = close,
            )
        }

        HistoryFilterSheet.START_DATE -> {
            DateFilterSheet(
                title = stringResource(R.string.history_filter_start_date_sheet_title),
                date = filters.startDate,
                onDateChanged = { date ->
                    close()
                    onStartDateChanged(date)
                },
                onDismiss = close,
            )
        }

        HistoryFilterSheet.END_DATE -> {
            DateFilterSheet(
                title = stringResource(R.string.history_filter_end_date_sheet_title),
                date = filters.endDate,
                onDateChanged = { date ->
                    close()
                    onEndDateChanged(date)
                },
                onDismiss = close,
            )
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun planChipLabel(plan: HistoryPlanFilter): String =
    when (plan) {
        HistoryPlanFilter.Any -> stringResource(R.string.history_filter_plan_any)
        HistoryPlanFilter.AdHocOnly -> stringResource(R.string.history_filter_plan_ad_hoc)
        is HistoryPlanFilter.Plan -> plan.planName
    }

/** A chip that opens a chooser. */
@Composable
private fun HistoryFilterChip(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = HistoryChipTapTarget)
                .clip(RepFlowShapes.pill)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(label = label, icon = icon, selected = selected)
    }
}

/** `Show invalidated`: a chip that is itself the switch. */
@Composable
private fun HistoryToggleChip(
    label: String,
    @DrawableRes icon: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .heightIn(min = HistoryChipTapTarget)
                .clip(RepFlowShapes.pill)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        ChipFace(label = label, icon = icon, selected = checked)
    }
}

@Composable
private fun ChipFace(
    label: String,
    @DrawableRes icon: Int,
    selected: Boolean,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (selected) selectedColors.fill else Color.Transparent
    val border = if (selected) Color.Transparent else RepFlowColor.hairline
    val content = if (selected) selectedColors.label else MaterialTheme.colorScheme.onSurface.copy(alpha = CHIP_LABEL_ALPHA)
    Row(
        modifier =
            Modifier
                .heightIn(min = HistoryChipHeight)
                .background(fill, RepFlowShapes.pill)
                .border(BorderStroke(1.dp, border), RepFlowShapes.pill)
                .padding(horizontal = ChipHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(ChipIconSize))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlanFilterSheet(
    selected: HistoryPlanFilter,
    options: List<HistoryPlanFilter.Plan>,
    onSelect: (HistoryPlanFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismiss, title = stringResource(R.string.history_filter_plan_sheet_title)) {
        val choices: List<Pair<HistoryPlanFilter, String>> =
            listOf(
                HistoryPlanFilter.Any to stringResource(R.string.history_filter_plan_any),
                HistoryPlanFilter.AdHocOnly to stringResource(R.string.history_filter_plan_ad_hoc),
            ) + options.map { it to it.planName }
        OptionRows(choices = choices, isSelected = { it == selected }, onSelect = onSelect)
    }
}

@Composable
private fun ExerciseFilterSheet(
    selectedId: ExerciseId?,
    options: List<HistoryExerciseFilterOption>,
    onSelect: (ExerciseId?) -> Unit,
    onDismiss: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismiss, title = stringResource(R.string.history_filter_exercise_sheet_title)) {
        val choices: List<Pair<ExerciseId?, String>> =
            listOf<Pair<ExerciseId?, String>>(null to stringResource(R.string.history_filter_exercise_all)) +
                options.map { it.id to it.name }
        OptionRows(choices = choices, isSelected = { it == selectedId }, onSelect = onSelect)
    }
}

/** A sheet's single-choice rows: the chosen one carries an accent check. */
@Composable
private fun <T> OptionRows(
    choices: List<Pair<T, String>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    val accent = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label
    Box(modifier = Modifier.heightIn(max = OptionListMaxHeight).verticalScroll(rememberScrollState())) {
        Column(modifier = Modifier.padding(horizontal = RepFlowSpacing.screenPadding)) {
            choices.forEachIndexed { index, (value, label) ->
                RepFlowListRow(
                    title = label,
                    onClick = { onSelect(value) },
                    showDivider = index < choices.lastIndex,
                    trailing = {
                        if (isSelected(value)) {
                            Icon(
                                painter = painterResource(RepFlowIcons.checkCircle),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(OptionCheckSize),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * One end of the date range: Material's date picker inside the sheet, with
 * `Clear` and `Set`. The picker works in UTC midnights, as the old dialog did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterSheet(
    title: String,
    date: LocalDate?,
    onDateChanged: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismiss, title = title) {
        val initialMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.history_date_picker_clear),
                onClick = { onDateChanged(null) },
                modifier = Modifier.weight(1f),
            )
            RepFlowPrimaryButton(
                text = stringResource(R.string.history_date_picker_confirm),
                onClick = {
                    val selectedMillis = pickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onDateChanged(Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** `6b`'s tap-target floor around each 36-tall chip. */
internal val HistoryChipTapTarget = 44.dp

/** `min-height:36px` (`:1840`). */
internal val HistoryChipHeight = 36.dp

/** The unselected chip's word: `rgba(233,233,237,.7)`. */
private const val CHIP_LABEL_ALPHA = 0.7f
private val ChipHorizontalPadding = 13.dp
private val ChipIconSize = 14.dp
private val OptionCheckSize = 18.dp
private val OptionListMaxHeight = 420.dp
