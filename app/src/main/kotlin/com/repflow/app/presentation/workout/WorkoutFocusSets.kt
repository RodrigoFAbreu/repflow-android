package com.repflow.app.presentation.workout

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal

/*
 * Focus mode's set list and the correction sheet a logged set opens
 * (remediation-1 CP8, `4a` `nFocusRows`, `RepFlow.dc.html:1260-1270`,
 * `:4118-4141`, and `nSetEditOpen`, `:1549-1581`).
 */

/** The logged sets, then one row per planned working set still to log. */
@Composable
internal fun FocusSetList(
    exercise: ActiveExerciseUi,
    onCorrectLast: () -> Unit,
) {
    val rows = exercise.focusSetRows()
    if (rows.isEmpty()) return
    Column {
        rows.forEach { row ->
            when (row) {
                is FocusSetRow.Logged -> LoggedSetRow(row, exercise.trackingType, onCorrect = onCorrectLast)
                is FocusSetRow.Pending -> PendingSetRow(row, exercise.plannedTarget)
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = SET_ROW_DIVIDER_ALPHA),
            )
        }
    }
}

/**
 * One logged set: the design's 26dp status disc (a check on accent for a
 * working set, the flame on a hairline for a warm-up), the summary, its
 * RPE/pain/technique line, and - on the most recently recorded set only - the
 * pencil that opens the correction sheet (`D31`).
 *
 * The summary line stays one node carrying exactly `primary + warm-up +
 * extra`: `ActiveWorkoutScreenTest` matches that concatenation by exact text,
 * so the two suffixes cannot be promoted into chips of their own. Its own
 * `Set N:` carries the set number the prototype draws in a separate column
 * (`D65`), so the disc is hidden from the accessibility tree.
 */
@Composable
private fun LoggedSetRow(
    row: FocusSetRow.Logged,
    trackingType: ExerciseTrackingType,
    onCorrect: () -> Unit,
) {
    val set = row.set
    val detail =
        listOfNotNull(
            set.rpe?.let { rpe -> stringResource(R.string.workout_active_set_rpe, rpe.toString()) },
            set.pain?.let { pain -> stringResource(R.string.workout_active_set_pain, pain) },
            set.techniqueQuality?.let { quality -> stringResource(R.string.workout_active_set_technique_quality, quality) },
        )
    val correctLabel = stringResource(R.string.workout_focus_correct_action)
    val selected = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    SetRowFrame(
        modifier =
            if (row.isLast) {
                Modifier.clickable(role = Role.Button, onClickLabel = correctLabel, onClick = onCorrect)
            } else {
                Modifier
            },
        disc = {
            if (set.isWarmup) {
                SetDisc(icon = RepFlowIcons.fire, fill = Color.Transparent, ring = RepFlowColor.hairline, tint = secondary)
            } else {
                SetDisc(icon = RepFlowIcons.checkFat, fill = selected.fill, ring = Color.Transparent, tint = selected.label)
            }
        },
        trailing =
            if (row.isLast) {
                {
                    Icon(
                        painter = painterResource(RepFlowIcons.pencilSimple),
                        contentDescription = null,
                        tint = secondary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            } else {
                null
            },
    ) {
        Text(
            text = loggedSetSummary(set, trackingType, row.isExtra),
            style = RepFlowNumericTextStyle.copy(fontSize = SetRowFontSize, lineHeight = SetRowLineHeight),
            color = if (set.isWarmup) secondary else MaterialTheme.colorScheme.onSurface,
        )
        if (detail.isNotEmpty()) {
            Text(
                text = detail.joinToString(separator = " · "),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
        }
    }
}

/**
 * A planned working set not logged yet: an open circle, `Set N: not logged`,
 * and the plan's target and rest - the values the old target chips carried.
 * One wrapping line, so a target too long for the width degrades visibly
 * instead of pushing a value off the right edge.
 */
@Composable
private fun PendingSetRow(
    row: FocusSetRow.Pending,
    target: PlannedTargetUi?,
) {
    val parts =
        listOfNotNull(
            stringResource(R.string.workout_focus_set_row_pending, row.setNumber),
            target?.repRange?.let { stringResource(R.string.workout_active_plan_rep_range, it.first, it.last) },
            target?.durationRangeSeconds?.let { stringResource(R.string.workout_active_plan_duration_range, it.first, it.last) },
            target?.restSeconds?.let { stringResource(R.string.workout_active_plan_rest, it) },
        )
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    SetRowFrame(
        disc = { SetDisc(icon = RepFlowIcons.circle, fill = Color.Transparent, ring = RepFlowColor.hairline, tint = secondary) },
    ) {
        Text(
            text = parts.joinToString(separator = " · "),
            style = RepFlowNumericTextStyle.copy(fontSize = SetRowFontSize, lineHeight = SetRowLineHeight),
            color = secondary,
        )
    }
}

/** `min-height:48px`, `padding:11px 2px`, gap 12. */
@Composable
private fun SetRowFrame(
    disc: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = SetRowMinHeight)
                .then(modifier)
                .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        disc()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
        trailing?.invoke()
    }
}

@Composable
private fun SetDisc(
    icon: Int,
    fill: Color,
    ring: Color,
    tint: Color,
) {
    Box(
        modifier =
            Modifier
                .size(SetDiscSize)
                .clip(CircleShape)
                .background(color = fill, shape = CircleShape)
                .border(BorderStroke(1.dp, ring), CircleShape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
    }
}

/**
 * One logged set's summary line, unchanged from before focus mode:
 * `primary + warm-up suffix + extra suffix`, assembled into a single string -
 * and therefore one text node, because `ActiveWorkoutScreenTest` matches
 * exactly that concatenation. The three formats carry the parent milestone's
 * defect fixes: a timed set never reads as a zero-load row, and a weight &
 * reps set with no recorded load reads as reps only, not `0.0 kg`.
 */
@Composable
private fun loggedSetSummary(
    set: ActiveSetUi,
    trackingType: ExerciseTrackingType,
    isExtra: Boolean,
): String {
    val warmupSuffix = if (set.isWarmup) " " + stringResource(R.string.workout_active_warmup_suffix) else ""
    val extraSuffix = if (isExtra) " " + stringResource(R.string.workout_active_set_extra_suffix) else ""
    val primaryText =
        when {
            trackingType == ExerciseTrackingType.DURATION -> {
                stringResource(R.string.workout_active_set_row_duration, set.setNumber, set.durationSeconds ?: 0)
            }

            trackingType == ExerciseTrackingType.WEIGHT_AND_REPS && set.load != null -> {
                stringResource(R.string.workout_active_set_row_weight_reps, set.setNumber, set.load, set.reps ?: 0)
            }

            else -> {
                stringResource(R.string.workout_active_set_row_reps, set.setNumber, set.reps ?: 0)
            }
        }
    return primaryText + warmupSuffix + extraSuffix
}

/**
 * The correction sheet (`4a` `nSetEditOpen`, `:1549-1581`), opened by the
 * pencil on the most recently logged set - the only set a correction can reach
 * (`D31`). It corrects what was lifted (weight, reps or seconds) through the
 * existing `EditLastWorkoutSet`; the set's RPE, pain, technique and warm-up
 * flag are passed back unchanged. There is no `Delete`: removing the last set
 * is `Undo last` (`D66`).
 */
@Composable
internal fun SetCorrectionSheet(
    exercise: ActiveExerciseUi,
    set: ActiveSetUi,
    onDismissRequest: () -> Unit,
    onSave: (load: BigDecimal?, reps: BigDecimal?, seconds: BigDecimal?) -> Unit,
) {
    var load by rememberSaveable(set.id.value) { mutableStateOf(set.load?.let(BigDecimal::valueOf)?.toPlainString()) }
    var reps by rememberSaveable(set.id.value) { mutableStateOf(set.reps?.toString()) }
    var seconds by rememberSaveable(set.id.value) { mutableStateOf(set.durationSeconds?.toString()) }
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column {
                Text(
                    text =
                        if (set.isWarmup) {
                            stringResource(R.string.workout_active_warmup_label)
                        } else {
                            stringResource(R.string.workout_focus_correct_title, set.setNumber)
                        },
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = SheetTitleFontSize, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.workout_focus_correct_subtitle, exercise.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    modifier = Modifier.padding(top = SheetSubtitleTopPadding),
                )
            }
            EntrySteppers(
                trackingType = exercise.trackingType,
                load = load?.toBigDecimal(),
                onLoadChange = { load = it.toPlainString() },
                reps = reps?.toBigDecimal(),
                onRepsChange = { reps = it.toPlainString() },
                seconds = seconds?.toBigDecimal(),
                onSecondsChange = { seconds = it.toPlainString() },
                loadStep = exercise.loadStep(),
                repRange = null,
                durationRange = null,
            )
            RepFlowPrimaryButton(
                text = stringResource(R.string.workout_focus_correct_save),
                onClick = { onSave(load?.toBigDecimal(), reps?.toBigDecimal(), seconds?.toBigDecimal()) },
                modifier = Modifier.fillMaxWidth().heightIn(min = SheetActionMinHeight),
            )
        }
    }
}

private const val SET_ROW_DIVIDER_ALPHA = 0.08f

private val SetRowMinHeight = 48.dp
private val SetDiscSize = 26.dp

/** The summary at 14.5, tabular: a set list whose digits change must not reflow. */
private val SetRowFontSize = 14.5.sp
private val SetRowLineHeight = 20.sp

private val SheetTitleFontSize = 19.sp
private val SheetSubtitleTopPadding = 3.dp
private val SheetActionMinHeight = 52.dp
