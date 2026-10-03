package com.repflow.app.presentation.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal
import java.time.ZoneId
import java.util.Locale

/*
 * `5b`'s chart card: the metric's caption and the `3m` / `6m` / `All` pills, the
 * value at 30/500 with the change over the range (`+10 kg · +14%`), a strip
 * reading the selected point, and the line chart ([ProgressChart]).
 *
 * States the design does not draw, decided here: no session in the range shows
 * `progress_metric_empty` in place of the value and chart (the pills stay, so a
 * wider range is one tap away); one session shows its value and readout and a
 * lone dot, with no delta; `Est. 1RM` with no eligible set says why (J6).
 */

@Composable
internal fun ProgressCard(
    exercise: ExerciseProgress,
    metric: ProgressMetric,
    range: ProgressRange,
    series: ProgressSeries?,
    zone: ZoneId,
    showsLoadMetricsUnavailable: Boolean,
    onRangeSelected: (ProgressRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val units = unitsOf(exercise.trackingType, metric)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val points = series?.points.orEmpty()
    RepFlowCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(CardPadding)) {
        CardHeader(
            caption = metricCaption(exercise.trackingType, metric),
            range = range,
            onRangeSelected = onRangeSelected,
        )
        if (series != null && points.isNotEmpty()) {
            ValueAndDelta(series = series, units = units)
            SpanLine(series = series, zone = zone)
            ChartWithReadout(series = series, metric = metric, range = range, zone = zone, unit = units.short)
        } else {
            MetricEmptyState(
                messageRes =
                    if (metric == ProgressMetric.ESTIMATED_ONE_REP_MAX && exercise.estimateNeedsShorterSet) {
                        R.string.progress_estimate_needs_shorter_set
                    } else {
                        R.string.progress_metric_empty
                    },
                modifier = Modifier.padding(top = ChartTopGap),
            )
        }
        if (showsLoadMetricsUnavailable) {
            Text(
                text =
                    stringResource(
                        if (exercise.trackingType == ExerciseTrackingType.DURATION) {
                            R.string.progress_load_metrics_unavailable_duration
                        } else {
                            R.string.progress_load_metrics_unavailable_reps
                        },
                    ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = secondary,
                modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
            )
        }
    }
}

@Composable
private fun CardHeader(
    caption: String,
    range: ProgressRange,
    onRangeSelected: (ProgressRange) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
        Text(
            text = caption.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.weight(1f),
        )
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(PillGap)) {
            ProgressRange.entries.forEach { entry ->
                RangePill(
                    label = rangeLabel(entry),
                    selected = entry == range,
                    onClick = { onRangeSelected(entry) },
                )
            }
        }
    }
}

/** A range pill: 11.5 text, a 32 tall pill inside a 44 tall touch target (registered), selected one tinted and in the medium weight. */
@Composable
private fun RangePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val shape = RepFlowShapes.pill
    Box(
        modifier =
            Modifier
                .heightIn(min = TouchTarget)
                .widthIn(min = TouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .heightIn(min = PillHeight)
                    .clip(shape)
                    .background(if (selected) colors.fill else Color.Transparent, shape)
                    .border(BorderStroke(1.dp, if (selected) colors.border else RepFlowColor.hairline), shape)
                    .padding(horizontal = PillHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = PillFontSize),
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) colors.label else MaterialTheme.colorScheme.onSurface.copy(alpha = PILL_LABEL_ALPHA),
            )
        }
    }
}

/** The latest value at 30/500 and, with a trend, `+10 kg · +14%` - the sign carries the direction, colour only repeats it. */
@Composable
private fun ValueAndDelta(
    series: ProgressSeries,
    units: ProgressUnits,
) {
    val locale = currentProgressLocale()
    val latest = requireNotNull(series.latest)
    val delta = series.delta
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ValueTopGap, bottom = ValueBottomGap),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Text(
            text = plainNumber(latest.value, locale),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = ValueFontSize, fontFeatureSettings = "tnum"),
            letterSpacing = ValueLetterSpacing,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = units.long,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = UnitFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.alignByBaseline(),
        )
        if (delta != null) {
            val percent = series.deltaPercent
            Text(
                text =
                    if (percent == null) {
                        stringResource(R.string.progress_delta, signedNumber(delta, locale), units.short)
                    } else {
                        stringResource(
                            R.string.progress_delta_percent,
                            signedNumber(delta, locale),
                            units.short,
                            signedNumber(percent, locale),
                        )
                    },
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = deltaColor(delta),
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** `5 Jul – 2 Oct 2026 · 30 sessions`, under the value (`8b`): the span the series covers and how many sessions are in it. */
@Composable
private fun SpanLine(
    series: ProgressSeries,
    zone: ZoneId,
) {
    val points = series.points
    val dates = spanDates(points.first().startedAt, points.last().startedAt, zone, currentProgressLocale())
    Text(
        text = pluralStringResource(R.plurals.progress_span, points.size, dates, points.size),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = SpanFontSize, fontFeatureSettings = "tnum"),
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        modifier = Modifier.padding(bottom = SpanBottomGap),
    )
}

/** Up (or level) in the selected-accent tone, down in the destructive one. */
@Composable
private fun deltaColor(delta: BigDecimal): Color =
    if (delta.signum() >= 0) repFlowSelectedPillColors(MaterialTheme.colorScheme).label else MaterialTheme.colorScheme.error

@Composable
private fun ChartWithReadout(
    series: ProgressSeries,
    metric: ProgressMetric,
    range: ProgressRange,
    zone: ZoneId,
    unit: String,
) {
    val locale = currentProgressLocale()
    val points = series.points
    var picked by remember(points) { mutableStateOf<Int?>(null) }
    val selectedIndex = (picked ?: points.lastIndex).coerceIn(0, points.lastIndex)
    val latestYear =
        points
            .last()
            .startedAt
            .atZone(zone)
            .year
    val spansYears =
        points
            .first()
            .startedAt
            .atZone(zone)
            .year != latestYear
    val dateLabels = points.map { dateLabel(it.startedAt, zone, locale, withYear = spansYears) }
    val selectedWhen =
        dateLabel(
            points[selectedIndex].startedAt,
            zone,
            locale,
            withYear =
                points[selectedIndex].startedAt.atZone(zone).year != latestYear,
        )
    val readoutWhen =
        if (selectedIndex == points.lastIndex) stringResource(R.string.progress_readout_latest, selectedWhen) else selectedWhen
    val readoutValue = stringResource(R.string.progress_value_with_unit, plainNumber(points[selectedIndex].value, locale), unit)
    SelectionReadout(whenText = readoutWhen, valueText = readoutValue)
    val description =
        pluralStringResource(
            R.plurals.progress_chart_description,
            points.size,
            metricLabel(metric),
            rangeSpoken(range),
            points.size,
            stringResource(R.string.progress_value_with_unit, plainNumber(points.first().value, locale), unit),
            stringResource(R.string.progress_value_with_unit, plainNumber(points.last().value, locale), unit),
        )
    ProgressChart(
        values = points.map { it.value },
        dateLabels = dateLabels,
        selectedIndex = selectedIndex,
        onSelect = { picked = it },
        description = description,
        modifier = Modifier.padding(top = ReadoutBottomGap),
    )
}

/** The strip above the chart (`5b`): `Latest · 12 Aug` or the picked date on the left, its value on the right; read out when it changes. */
@Composable
private fun SelectionReadout(
    whenText: String,
    valueText: String,
) {
    val shape = RoundedCornerShape(ReadoutRadius)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = READOUT_FILL_ALPHA), shape)
                .padding(horizontal = ReadoutHorizontalPadding, vertical = ReadoutVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(ReadoutGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = whenText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = ReadoutWhenFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = ReadoutValueFontSize, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

/** No point in the range: `1d`'s glyph at 35% and one line. */
@Composable
private fun MetricEmptyState(
    @StringRes messageRes: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = RepFlowSpacing.gapLg),
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
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EmptyTextSize),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun currentProgressLocale(): Locale = LocalConfiguration.current.locales[0]

private const val PILL_LABEL_ALPHA = 0.7f
private const val READOUT_FILL_ALPHA = 0.05f

private val CardPadding = 14.dp
private val ValueFontSize = 30.sp
private const val VALUE_LETTER_SPACING_EM = -0.02f
private val ValueLetterSpacing = VALUE_LETTER_SPACING_EM.em
private val UnitFontSize = 13.sp
private val ValueTopGap = 4.dp
private val ValueBottomGap = 2.dp
private val SpanBottomGap = 8.dp
private val SpanFontSize = 12.sp
private val ChartTopGap = 14.dp
private val ReadoutBottomGap = 8.dp
private val ReadoutRadius = 9.dp
private val ReadoutHorizontalPadding = 10.dp
private val ReadoutVerticalPadding = 7.dp
private val ReadoutGap = 10.dp
private val ReadoutWhenFontSize = 12.5.sp
private val ReadoutValueFontSize = 13.5.sp
private val PillGap = 5.dp
private val PillHeight = 32.dp
private val PillHorizontalPadding = 9.dp
private val PillFontSize = 11.5.sp
private val TouchTarget = 44.dp
