package com.repflow.app.presentation.progress

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The Progress tab (remediation-1-remediation-1 CP3), `5b`: the title, a
 * full-width exercise picker button and the `Top set` / `Est. 1RM` / `Volume`
 * buttons stay put; under them one scrolling column holds the chart card
 * ([ProgressCard]), the tiles, `Training frequency` and `Records`
 * ([ProgressStatsSection]), and the note that only valid sessions count.
 *
 * The chosen metric button is accent-tinted **and** carries a check, so the
 * choice is never shown by colour alone (`6b`).
 */

@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onExerciseSelected: (ExerciseId) -> Unit,
    onMetricSelected: (ProgressMetric) -> Unit,
    onRangeSelected: (ProgressRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    RepFlowScreenScaffold(title = stringResource(R.string.nav_progress_title), modifier = modifier) { padding ->
        val exercise = uiState.exercise
        when {
            uiState.isLoading -> {
                RepFlowLoadingIndicator(Modifier.padding(padding))
            }

            exercise == null -> {
                ProgressEmptyState(Modifier.padding(padding))
            }

            else -> {
                ProgressContent(
                    uiState = uiState,
                    exercise = exercise,
                    padding = padding,
                    onExerciseSelected = onExerciseSelected,
                    onMetricSelected = onMetricSelected,
                    onRangeSelected = onRangeSelected,
                )
            }
        }
    }
}

@Composable
private fun ProgressContent(
    uiState: ProgressUiState,
    exercise: ExerciseProgress,
    padding: PaddingValues,
    onExerciseSelected: (ExerciseId) -> Unit,
    onMetricSelected: (ProgressMetric) -> Unit,
    onRangeSelected: (ProgressRange) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        ExercisePickerButton(name = exercise.name, onClick = { pickerOpen = true }, modifier = Modifier.padding(top = TitleGap))
        MetricButtons(
            offered = exercise.offeredMetrics,
            selected = uiState.metric,
            onSelect = onMetricSelected,
            modifier = Modifier.padding(top = RepFlowSpacing.gapMd),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = BodyTopGap, bottom = RepFlowSpacing.gapSm),
        ) {
            ProgressCard(
                exercise = exercise,
                metric = uiState.metric,
                range = uiState.range,
                series = uiState.series,
                zone = uiState.zone,
                showsLoadMetricsUnavailable = uiState.showsLoadMetricsUnavailable,
                onRangeSelected = onRangeSelected,
            )
            uiState.stats?.let { stats ->
                ProgressStatsSection(
                    stats = stats,
                    unit = unitsOf(exercise.trackingType, uiState.metric).short,
                    now = uiState.now,
                    zone = uiState.zone,
                )
            }
        }
    }
    if (pickerOpen) {
        ExercisePickerSheet(
            exercises = uiState.exercises,
            selectedId = exercise.exerciseId,
            onSelect = {
                pickerOpen = false
                onExerciseSelected(it)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/**
 * `5b`'s metric buttons: three equal buttons 6 apart, each 40 tall at radius 8
 * with 13 text inside a 44 tall touch target (registered). A reps-only or
 * timed exercise offers `Top set` alone (`D22`).
 */
@Composable
private fun MetricButtons(
    offered: List<ProgressMetric>,
    selected: ProgressMetric,
    onSelect: (ProgressMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MetricGap),
    ) {
        offered.forEach { metric ->
            MetricButton(
                label = metricLabel(metric),
                selected = metric == selected,
                onClick = { onSelect(metric) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val content = if (selected) colors.label else repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Box(
        modifier =
            modifier
                .heightIn(min = TouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = MetricHeight)
                    .clip(MetricShape)
                    .background(if (selected) colors.fill else Color.Transparent, MetricShape)
                    .border(BorderStroke(1.dp, if (selected) colors.border else RepFlowColor.hairline), MetricShape),
            horizontalArrangement = Arrangement.spacedBy(CheckGap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(RepFlowIcons.checkFat),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(CheckSize),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = MetricFontSize),
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun metricLabel(metric: ProgressMetric): String =
    when (metric) {
        ProgressMetric.TOP_SET -> stringResource(R.string.progress_metric_top_set)
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> stringResource(R.string.progress_metric_estimated_one_rep_max)
        ProgressMetric.VOLUME -> stringResource(R.string.progress_metric_volume)
    }

/**
 * Nothing trained yet: `1d`'s empty state - a 26 glyph at 35% and one 13.5
 * line - the treatment the CP2 placeholder already used, with its words.
 */
@Composable
private fun ProgressEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.chartLineUp),
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = EMPTY_GLYPH_ALPHA),
                modifier = Modifier.size(EmptyGlyphSize),
            )
            Text(
                text = stringResource(R.string.progress_empty),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = EmptyTextSize),
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal const val EMPTY_GLYPH_ALPHA = 0.35f

/** The title's `margin-bottom:12px`. */
private val TitleGap = 12.dp
private val BodyTopGap = 14.dp
private val MetricGap = 6.dp
private val MetricHeight = 40.dp
private val TouchTarget = 44.dp
private val MetricShape = RoundedCornerShape(8.dp)
private val MetricFontSize = 13.sp
private val CheckSize = 13.dp
private val CheckGap = 5.dp
internal val EmptyGlyphSize = 26.dp
internal val EmptyTextSize = 13.5.sp
