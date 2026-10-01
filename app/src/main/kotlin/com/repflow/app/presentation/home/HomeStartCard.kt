package com.repflow.app.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowCardDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowListRow
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.isDarkColorScheme

/*
 * Home's start card and start sheet (remediation-1 CP5): `4a`'s `:754-761`
 * and `nStartSheet` (`:1407-1435`), and `1d`'s first-run card
 * (`:3020-3028`).
 */

private val StartCardPadding = PaddingValues(18.dp)
private val StartSheetRowMinHeight = 60.dp

/**
 * The start card's ground: `4a`'s 160-degree gradient from `#262a60` to
 * `#232532` with a `#423a6a` ring in dark; `1d`'s plain surface and hairline
 * in light. The label takes the accent pair the accent-outline tier uses.
 */
internal data class HomeStartCardColors(
    val fillTop: Color,
    val fillBottom: Color,
    val ring: Color,
    val label: Color,
)

internal fun homeStartCardColors(
    scheme: ColorScheme,
    hairline: Color,
): HomeStartCardColors {
    val label = repFlowAccentOutlineColors(scheme).label
    return if (isDarkColorScheme(scheme)) {
        HomeStartCardColors(
            fillTop = Color(START_FILL_TOP_DARK),
            fillBottom = scheme.surface,
            ring = Color(START_RING_DARK),
            label = label,
        )
    } else {
        HomeStartCardColors(fillTop = scheme.surface, fillBottom = scheme.surface, ring = hairline, label = label)
    }
}

private const val START_FILL_TOP_DARK = 0xFF262A60
private const val START_RING_DARK = 0xFF423A6A
private const val START_GRADIENT_END_STOP = 0.62f

/**
 * The start card: with a plan, `4a`'s card (`:754-761`) - the plan, its
 * size, `Start workout` and `Train something else`; with none, `1d`'s
 * first-run card (`:3020-3028`).
 */
@Composable
internal fun StartCard(
    start: HomeStartCard,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
    onTrainSomethingElse: () -> Unit,
    onCreatePlanClick: () -> Unit,
) {
    when (start) {
        HomeStartCard.Loading -> Unit
        is HomeStartCard.Plan -> PlanStartCard(start.option, onStartWorkout, onTrainSomethingElse)
        HomeStartCard.NoPlan -> FirstRunCard(onCreatePlanClick = onCreatePlanClick, onEmptyWorkout = { onStartWorkout(null) })
    }
}

@Composable
private fun PlanStartCard(
    option: HomePlanOption,
    onStartWorkout: (TrainingPlanVersionId?) -> Unit,
    onTrainSomethingElse: () -> Unit,
) {
    val colors = homeStartCardColors(MaterialTheme.colorScheme, RepFlowColor.hairline)
    val shape = RepFlowCardDefaults.shape
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    brush =
                        Brush.verticalGradient(
                            0f to colors.fillTop,
                            START_GRADIENT_END_STOP to colors.fillBottom,
                        ),
                    shape = shape,
                ).border(BorderStroke(RepFlowCardDefaults.borderWidth, colors.ring), shape)
                .padding(StartCardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CardActionsGap)) {
            Icon(
                painter = painterResource(RepFlowIcons.calendarCheck),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(SmallGlyphSize),
            )
            Text(
                text = stringResource(R.string.home_start_label).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.label,
            )
        }
        Text(
            text = option.planName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = RepFlowSpacing.gapMd),
        )
        Text(
            text = planOptionMeta(option),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = homeMetaColor(),
            modifier = Modifier.padding(top = RepFlowSpacing.gapXs),
        )
        RepFlowPrimaryButton(
            text = stringResource(R.string.home_start_workout),
            onClick = { onStartWorkout(option.versionId) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            leadingIcon = RepFlowIcons.playFill,
        )
        TextButton(
            onClick = onTrainSomethingElse,
            modifier = Modifier.fillMaxWidth().padding(top = CardActionsGap).heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.home_train_something_else),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = colors.label,
            )
            Spacer(Modifier.width(7.dp))
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(SmallGlyphSize),
            )
        }
    }
}

@Composable
private fun planOptionMeta(option: HomePlanOption): String =
    stringResource(
        R.string.home_start_meta,
        pluralStringResource(R.plurals.home_exercise_count, option.exerciseCount, option.exerciseCount),
        pluralStringResource(R.plurals.home_working_set_count, option.workingSetCount, option.workingSetCount),
    )

@Composable
private fun FirstRunCard(
    onCreatePlanClick: () -> Unit,
    onEmptyWorkout: () -> Unit,
) {
    RepFlowCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        EmptyStateBody(glyph = RepFlowIcons.listPlus, message = stringResource(R.string.home_first_run_message))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CardActionsGap)) {
            RepFlowPrimaryButton(
                text = stringResource(R.string.home_create_plan),
                onClick = onCreatePlanClick,
                modifier = Modifier.weight(1f),
            )
            RepFlowAccentOutlineButton(
                text = stringResource(R.string.home_empty_workout),
                onClick = onEmptyWorkout,
                modifier = Modifier.weight(1f).heightIn(min = ActionRowMinHeight),
            )
        }
    }
}

/**
 * The start sheet (`nStartSheet`, `:1407-1435`): every active plan as one row,
 * then `Empty workout`. The design's rows are a plan's days and it ends with
 * `A different plan ›`; RepFlow's plans have no days (D4), so the rows are the
 * plans themselves and that last row has nothing left to open (D45).
 */
@Composable
internal fun StartSheet(
    options: List<HomePlanOption>,
    onPick: (TrainingPlanVersionId?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    RepFlowSheet(
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.home_start_sheet_title).takeIf { options.isNotEmpty() },
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp)) {
            options.forEach { option ->
                RepFlowListRow(
                    title = option.planName,
                    meta = planOptionMeta(option),
                    onClick = { onPick(option.versionId) },
                    minHeight = StartSheetRowMinHeight,
                    showDivider = false,
                )
            }
            if (options.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = CardActionsGap),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
                )
            }
            RepFlowListRow(
                title = stringResource(R.string.home_empty_workout),
                meta = stringResource(R.string.home_empty_workout_meta),
                onClick = { onPick(null) },
                minHeight = StartSheetRowMinHeight,
                showDivider = false,
                leading = {
                    Icon(
                        painter = painterResource(RepFlowIcons.lightning),
                        contentDescription = null,
                        tint = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
                        modifier = Modifier.size(19.dp),
                    )
                },
                trailing = {},
            )
        }
    }
}
