package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.exercise.editor.InlineError

/**
 * One planned exercise (remediation-1 CP11). Collapsed, it is `4a`'s row
 * (`RepFlow.dc.html:905-917`, min-height 64): the name, `3 × 8–12 reps · 90s
 * rest` (plus `2a`'s warm-up count and `optional` badge), and a caret. Tapping
 * it expands it in place (`4a`'s `nPlanRowSel`) into [PlannedExerciseTargets];
 * a row with no exercise yet opens the picker sheet instead.
 *
 * `2a`'s up / down carets (`:2239-2242`) sit on the collapsed row, as drawn,
 * and the remove sits on it too: reorder and remove stay one tap away whether
 * or not the row is open, as they were before the conversion.
 */
@Composable
internal fun PlannedExerciseRow(
    row: PlannedExerciseRowUiState,
    expanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: TrainingPlanEditorRowActions,
    onHeaderClick: () -> Unit,
    onChangeExerciseClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReorderCarets(row.rowId, canMoveUp, canMoveDown, actions)
            RowSummary(
                row = row,
                expanded = expanded,
                onClick = onHeaderClick,
                modifier = Modifier.weight(1f),
            )
            RemoveButton(onClick = { actions.onRemove(row.rowId) })
        }
        if (expanded && row.exerciseId != null) {
            PlannedExerciseTargets(row = row, actions = actions, onChangeExerciseClick = onChangeExerciseClick)
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
        )
    }
}

/**
 * The tappable middle of the row: the name (or the picker prompt), its meta
 * line, the `optional` badge, and the expand caret. A row whose targets do not
 * validate says so here, so a collapsed row never hides why `Save` is off.
 */
@Composable
private fun RowSummary(
    row: PlannedExerciseRowUiState,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hasExercise = row.exerciseId != null
    val clickLabel =
        stringResource(
            when {
                !hasExercise -> R.string.training_plan_editor_row_choose_exercise
                expanded -> R.string.training_plan_editor_row_collapse
                else -> R.string.training_plan_editor_row_expand
            },
        )
    Row(
        modifier =
            modifier
                .heightIn(min = RowMinHeight)
                .clip(SummaryShape)
                .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
                .padding(horizontal = SummaryHorizontalPadding, vertical = SummaryVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.exerciseName.ifBlank { stringResource(R.string.training_plan_editor_select_exercise_placeholder) },
                style = MaterialTheme.typography.titleMedium,
                color = if (hasExercise) scheme.onSurface else repFlowAccentOutlineColors(scheme).label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasExercise) {
                RowMeta(row)
            }
        }
        if (row.isOptional) {
            Text(
                text = stringResource(R.string.training_plan_editor_row_optional_badge),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = BadgeFontSize),
                color = repFlowSecondaryTextColor(scheme),
                modifier =
                    Modifier
                        .background(scheme.onSurface.copy(alpha = BADGE_FILL_ALPHA), RepFlowShapes.pill)
                        .padding(horizontal = BadgeHorizontalPadding, vertical = BadgeVerticalPadding),
            )
        }
        if (hasExercise) {
            Icon(
                painter = painterResource(if (expanded) RepFlowIcons.caretUp else RepFlowIcons.caretDown),
                contentDescription = null,
                tint = scheme.onSurface.copy(alpha = RepFlowColor.trailingCaretAlpha),
                modifier = Modifier.size(CaretSize),
            )
        }
    }
}

/** `4a`'s meta (`:3666`) - `3 × 8–12 reps · 120s rest` - with `2a`'s warm-up count; an invalid row says so instead. */
@Composable
private fun RowMeta(row: PlannedExerciseRowUiState) {
    if (!row.hasNoErrors) {
        Box(modifier = Modifier.padding(top = MetaTopGap)) {
            InlineError(stringResource(R.string.training_plan_editor_row_needs_attention))
        }
        return
    }
    val sets = row.targetSetsText.trim().ifEmpty { MISSING_VALUE }
    val target =
        if (row.trackingType == ExerciseTrackingType.DURATION) {
            stringResource(
                R.string.training_plan_editor_row_meta_duration,
                sets,
                row.durationMinText.orMissing(),
                row.durationMaxText.orMissing(),
            )
        } else {
            stringResource(R.string.training_plan_editor_row_meta_reps, sets, row.repMinText.orMissing(), row.repMaxText.orMissing())
        }
    val parts =
        listOfNotNull(
            target,
            row.targetWarmupSetsText.trim().takeIf { (it.toIntOrNull() ?: 0) > 0 }?.let {
                stringResource(R.string.training_plan_editor_row_meta_warmup, it)
            },
            row.restSecondsText.trim().takeIf { it.isNotEmpty() }?.let {
                stringResource(R.string.training_plan_editor_row_meta_rest, it)
            },
        )
    Text(
        text = parts.joinToString(separator = META_SEPARATOR),
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        modifier = Modifier.padding(top = MetaTopGap),
    )
}

/**
 * `2a`'s reorder carets, stacked on the row's leading edge. Each is 44 wide
 * (`6b`'s floor) and 32 tall, so the pair fits `4a`'s 64 row - `2a` draws them
 * 36×28. An unavailable direction is dimmed, as drawn, and disabled.
 */
@Composable
private fun ReorderCarets(
    rowId: Long,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: TrainingPlanEditorRowActions,
) {
    Column {
        CaretButton(
            icon = RepFlowIcons.caretUp,
            contentDescription = stringResource(R.string.training_plan_editor_row_move_up_content_description),
            enabled = canMoveUp,
            onClick = { actions.onMoveUp(rowId) },
        )
        CaretButton(
            icon = RepFlowIcons.caretDown,
            contentDescription = stringResource(R.string.training_plan_editor_row_move_down_content_description),
            enabled = canMoveDown,
            onClick = { actions.onMoveDown(rowId) },
        )
    }
}

@Composable
private fun CaretButton(
    icon: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(width = CaretButtonWidth, height = CaretButtonHeight)
                .clip(SummaryShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription }
                .alpha(if (enabled) 1f else DISABLED_CARET_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CARET_TINT_ALPHA),
            modifier = Modifier.size(CaretSize),
        )
    }
}

/** Remove, on the row face (`4a`'s `Remove from this day` moved out of the expanded body): a 44 `trash` in the destructive tone. */
@Composable
private fun RemoveButton(onClick: () -> Unit) {
    val description = stringResource(R.string.training_plan_editor_row_remove_content_description)
    Box(
        modifier =
            Modifier
                .size(RemoveButtonSize)
                .clip(SummaryShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.trash),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(CaretSize),
        )
    }
}

/**
 * `2a`'s `Optional` (`:2329`): a 48-tall toggle at radius 10 with a
 * `check-circle`, accent-tinted while on - a checkbox to accessibility, so the
 * state is spoken as well as tinted.
 */
@Composable
internal fun OptionalToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (checked) selected.fill else Color.Transparent
    val border = if (checked) selected.border else RepFlowColor.hairline
    val label = if (checked) selected.label else repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier =
            modifier
                .heightIn(min = ToggleMinHeight)
                .clip(ToggleShape)
                .background(fill, ToggleShape)
                .border(BorderStroke(1.dp, border), ToggleShape)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.checkCircle),
            contentDescription = null,
            tint = label,
            modifier = Modifier.size(CaretSize),
        )
        Text(
            text = stringResource(R.string.training_plan_editor_row_optional_label),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = ToggleFontSize),
            color = label,
            modifier = Modifier.padding(start = ToggleGlyphGap),
        )
    }
}

/** `Change exercise`: re-opens the picker for this row, the old row dropdown's job. */
@Composable
internal fun ChangeExerciseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RepFlowNeutralOutlineButton(
        text = stringResource(R.string.training_plan_editor_row_change_exercise),
        onClick = onClick,
        modifier = modifier.heightIn(min = ToggleMinHeight),
    )
}

private fun String.orMissing(): String = trim().ifEmpty { MISSING_VALUE }

private const val MISSING_VALUE = "–"
private const val META_SEPARATOR = " · "
private val RowMinHeight = 64.dp
private val SummaryShape = RoundedCornerShape(8.dp)
private val SummaryHorizontalPadding = 6.dp
private val SummaryVerticalPadding = 10.dp
private val MetaTopGap = 3.dp
private val CaretSize = 16.dp
private val CaretButtonWidth = 44.dp
private val CaretButtonHeight = 32.dp
private const val CARET_TINT_ALPHA = 0.7f
private const val DISABLED_CARET_ALPHA = 0.35f
private val RemoveButtonSize = 44.dp
private val BadgeFontSize = 11.sp
private const val BADGE_FILL_ALPHA = 0.08f
private val BadgeHorizontalPadding = 8.dp
private val BadgeVerticalPadding = 3.dp
private val ToggleMinHeight = 48.dp
private val ToggleShape = RoundedCornerShape(10.dp)
private val ToggleFontSize = 14.sp
private val ToggleGlyphGap = 7.dp
