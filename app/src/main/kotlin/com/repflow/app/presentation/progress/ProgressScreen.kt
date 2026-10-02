package com.repflow.app.presentation.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The Progress tab (remediation-1 CP15), `4a`'s `nTabProgress`
 * (`RepFlow.dc.html:1013-1050`, script `:3743-3771`): the title, a row of
 * exercise chips, the `Top set` / `Est. 1RM` / `Volume` segmented control, the
 * card ([ProgressCard]) and the note that only valid sessions count.
 *
 * The chips scroll sideways as `4a` draws them (`overflow:auto`); the selected
 * chip and segment are accent-tinted **and** carry a check, so the choice is
 * never shown by colour alone (`6b`).
 */

@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onExerciseSelected: (ExerciseId) -> Unit,
    onMetricSelected: (ProgressMetric) -> Unit,
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
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(top = TitleGap, bottom = RepFlowSpacing.gapSm),
    ) {
        ExerciseChips(exercises = uiState.exercises, selectedId = exercise.exerciseId, onSelect = onExerciseSelected)
        MetricSegments(
            offered = exercise.offeredMetrics,
            selected = uiState.metric,
            onSelect = onMetricSelected,
            modifier = Modifier.padding(vertical = RepFlowSpacing.gapLg),
        )
        ProgressCard(
            exercise = exercise,
            metric = uiState.metric,
            series = uiState.series,
            showsLoadMetricsUnavailable = uiState.showsLoadMetricsUnavailable,
        )
        ValidSessionsNote(Modifier.padding(top = NoteTopGap))
    }
}

/** `4a`'s exercise chips (`:1016-1019`): 12.5 text, padding 8/13, 40 tall, a pill, 6 apart, in a sideways-scrolling row. */
@Composable
private fun ExerciseChips(
    exercises: List<ExerciseProgress>,
    selectedId: ExerciseId,
    onSelect: (ExerciseId) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        exercises.forEach { exercise ->
            ExerciseChip(
                label = exercise.name,
                selected = exercise.exerciseId == selectedId,
                onClick = { onSelect(exercise.exerciseId) },
            )
        }
    }
}

@Composable
private fun ExerciseChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (selected) selectedColors.fill else Color.Transparent
    val border = if (selected) selectedColors.border else RepFlowColor.hairline
    val content = if (selected) selectedColors.label else MaterialTheme.colorScheme.onSurface.copy(alpha = CHIP_LABEL_ALPHA)
    Row(
        modifier =
            Modifier
                .heightIn(min = ChipMinHeight)
                .clip(RepFlowShapes.pill)
                .background(fill, RepFlowShapes.pill)
                .border(BorderStroke(1.dp, border), RepFlowShapes.pill)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = ChipHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CheckGap),
    ) {
        if (selected) {
            Icon(
                painter = painterResource(RepFlowIcons.checkFat),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(CheckSize),
            )
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = content, maxLines = 1, softWrap = false)
    }
}

/**
 * `4a`'s metric control (`:1021-1025`): a hairline ring, radius 10, padding 3,
 * segments 40 tall at radius 8, 13 text, 4 apart. A reps-only or timed
 * exercise offers `Top set` alone (`D22`).
 */
@Composable
private fun MetricSegments(
    offered: List<ProgressMetric>,
    selected: ProgressMetric,
    onSelect: (ProgressMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, RepFlowColor.hairline), SegmentRingShape)
                .padding(SegmentRingPadding)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        offered.forEach { metric ->
            MetricSegment(
                label = metricLabel(metric),
                selected = metric == selected,
                onClick = { onSelect(metric) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val content = if (selected) selectedColors.label else repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier =
            modifier
                .heightIn(min = SegmentMinHeight)
                .clip(SegmentShape)
                .background(if (selected) selectedColors.fill else Color.Transparent, SegmentShape)
                .then(if (selected) Modifier.border(BorderStroke(1.dp, selectedColors.border), SegmentShape) else Modifier)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
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
            style = MaterialTheme.typography.bodySmall.copy(fontSize = SegmentFontSize),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun metricLabel(metric: ProgressMetric): String =
    when (metric) {
        ProgressMetric.TOP_SET -> stringResource(R.string.progress_metric_top_set)
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> stringResource(R.string.progress_metric_estimated_one_rep_max)
        ProgressMetric.VOLUME -> stringResource(R.string.progress_metric_volume)
    }

/** "Only valid sessions count…" with `ph-info` (`:1045-1048`, plan item 4). */
@Composable
private fun ValidSessionsNote(modifier: Modifier = Modifier) {
    val color = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NoteIconGap)) {
        Icon(
            painter = painterResource(RepFlowIcons.info),
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 1.dp).size(NoteIconSize),
        )
        Text(
            text = stringResource(R.string.progress_valid_sessions_note),
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = NoteLineHeight),
            color = color,
        )
    }
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

/** The unselected chip's word: `rgba(233,233,237,.7)`. */
private const val CHIP_LABEL_ALPHA = 0.7f
internal const val EMPTY_GLYPH_ALPHA = 0.35f

/** The title's `margin-bottom:12px`. */
private val TitleGap = 12.dp
private val ChipMinHeight = 44.dp
private val ChipHorizontalPadding = 13.dp
private val CheckSize = 13.dp
private val CheckGap = 5.dp
private val SegmentRingShape = RoundedCornerShape(10.dp)
private val SegmentShape = RoundedCornerShape(8.dp)
private val SegmentRingPadding = 3.dp
private val SegmentGap = 4.dp
private val SegmentMinHeight = 44.dp
private val SegmentFontSize = 13.sp
private val NoteTopGap = 14.dp
private val NoteIconGap = 9.dp
private val NoteIconSize = 15.dp
private val NoteLineHeight = 19.sp
internal val EmptyGlyphSize = 26.dp
internal val EmptyTextSize = 13.5.sp
