package com.repflow.app.presentation.progress

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/*
 * `4a`'s Progress card (`RepFlow.dc.html:1026-1044`, script `:3758-3771`): the
 * exercise and the delta since the window's first session, the latest value at
 * 30/500 with its unit, one bar per session in the window - the latest in the
 * accent, a month under each bar where a month starts - and the best in the
 * window. Bars are drawn with Compose `Canvas`, no charting dependency (plan
 * item 5).
 *
 * An offered metric with fewer than two points shows the empty state instead
 * of the chart (plan item 7); a reps-only or timed exercise also says why it
 * offers `Top set` alone (plan item 6, `D22`).
 */

@Composable
internal fun ProgressCard(
    exercise: ExerciseProgress,
    metric: ProgressMetric,
    series: ProgressSeries?,
    showsLoadMetricsUnavailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val units = unitsOf(exercise.trackingType, metric)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    RepFlowCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CardPadding),
    ) {
        CardHeader(name = exercise.name, series = series, deltaUnit = units.short)
        series?.latest?.let { latest ->
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
            ) {
                Text(
                    text = plainNumber(latest.value, currentLocale()),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = ValueFontSize, fontFeatureSettings = "tnum"),
                    letterSpacing = ValueLetterSpacing,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = units.long,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = UnitFontSize),
                    color = secondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        if (series != null && series.hasTrend) {
            BarChart(series = series, metric = metric, unit = units.short, modifier = Modifier.padding(top = ChartTopGap))
            Text(
                text =
                    stringResource(
                        R.string.progress_best,
                        plainNumber(requireNotNull(series.best), currentLocale()),
                        units.short,
                        series.points.size,
                    ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                color = secondary,
                modifier = Modifier.padding(top = RepFlowSpacing.gapMd),
            )
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
                text = stringResource(R.string.progress_load_metrics_unavailable),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = secondary,
                modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
            )
        }
    }
}

/** The exercise at 13 (`.6`) and, with a trend, `+10 kg since 5 May` - in the accent when up, destructive when down; the sign says which either way. */
@Composable
private fun CardHeader(
    name: String,
    series: ProgressSeries?,
    deltaUnit: String,
) {
    val delta = series?.delta
    val since = series?.first
    Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (delta != null && since != null) {
            Text(
                text =
                    stringResource(
                        R.string.progress_delta,
                        signedNumber(delta, currentLocale()),
                        deltaUnit,
                        sinceDate(since.startedAt, series),
                    ),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = deltaColor(delta),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** Up (or level) in the selected-accent tone, down in the destructive one. */
@Composable
private fun deltaColor(delta: BigDecimal): Color =
    if (delta.signum() >= 0) repFlowSelectedPillColors(MaterialTheme.colorScheme).label else MaterialTheme.colorScheme.error

/** `5 May`, or `5 May 2025` when the window started in an earlier year than its latest session. */
@Composable
private fun sinceDate(
    startedAt: Instant,
    series: ProgressSeries,
): String {
    val zone = ZoneId.systemDefault()
    val locale = currentLocale()
    val start = startedAt.atZone(zone)
    val latestYear =
        series.latest
            ?.startedAt
            ?.atZone(zone)
            ?.year
    val pattern = if (latestYear == null || latestYear == start.year) SINCE_PATTERN else SINCE_PATTERN_WITH_YEAR
    return DateTimeFormatter.ofPattern(pattern, locale).format(start)
}

/**
 * The bars (`:1035-1042`): 132 tall in all, 5 apart, radius 3 at the top; the
 * latest in the accent, the rest at half its strength; a 10 month label under
 * each bar where a month starts. One spoken description stands for the chart.
 */
@Composable
private fun BarChart(
    series: ProgressSeries,
    metric: ProgressMetric,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val window = series.points
    val heights = barHeightFractions(window.map { it.value })
    val months = barMonthLabels(window, ZoneId.systemDefault())
    val locale = currentLocale()
    val accent = MaterialTheme.colorScheme.primary
    val older = accent.copy(alpha = OLDER_BAR_ALPHA)
    val description =
        pluralStringResource(
            R.plurals.progress_chart_description,
            window.size,
            metricLabel(metric),
            window.size,
            stringResource(R.string.progress_value_with_unit, plainNumber(window.first().value, currentLocale()), unit),
            stringResource(R.string.progress_value_with_unit, plainNumber(window.last().value, currentLocale()), unit),
        )
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PlotHeight)
                    .semantics { contentDescription = description },
        ) {
            val gap = BarGap.toPx()
            val barWidth = (size.width - gap * (heights.size - 1)) / heights.size
            val radius = CornerRadius(BarRadius.toPx())
            heights.forEachIndexed { index, fraction ->
                val barHeight = size.height * fraction
                val left = index * (barWidth + gap)
                val path =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(Offset(left, size.height - barHeight), Size(barWidth, barHeight)),
                                topLeft = radius,
                                topRight = radius,
                                bottomRight = CornerRadius.Zero,
                                bottomLeft = CornerRadius.Zero,
                            ),
                        )
                    }
                drawPath(path, color = if (index == heights.lastIndex) accent else older)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = LabelTopGap).clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(BarGap),
        ) {
            months.forEach { month ->
                Text(
                    text = month?.month?.getDisplayName(TextStyle.SHORT, locale).orEmpty(),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 0.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Fewer than two points for an offered metric (plan item 7): `1d`'s glyph at 35% and one line. */
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

/** The value's unit (`kg`, `kg total`, `reps`, `s`) and the shorter one the delta and best line use. */
private data class ProgressUnits(
    val long: String,
    val short: String,
)

@Composable
private fun unitsOf(
    trackingType: ExerciseTrackingType,
    metric: ProgressMetric,
): ProgressUnits {
    val short =
        when (trackingType) {
            ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.progress_unit_kg)
            ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.progress_unit_reps)
            ExerciseTrackingType.DURATION -> stringResource(R.string.progress_unit_seconds)
        }
    val long = if (metric == ProgressMetric.VOLUME) stringResource(R.string.progress_unit_kg_total) else short
    return ProgressUnits(long = long, short = short)
}

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

private const val SINCE_PATTERN = "d MMM"
private const val SINCE_PATTERN_WITH_YEAR = "d MMM yyyy"

/** `#5d5294` against the latest bar's `#9184d9`: the accent at about half strength, in both themes. */
private const val OLDER_BAR_ALPHA = 0.5f

private val CardPadding = 14.dp
private val ValueFontSize = 30.sp
private const val VALUE_LETTER_SPACING_EM = -0.02f
private val ValueLetterSpacing = VALUE_LETTER_SPACING_EM.em
private val UnitFontSize = 13.sp
private val ChartTopGap = 14.dp

/** The 132 of `:1035` less the month row beneath it. */
private val PlotHeight = 112.dp
private val LabelTopGap = 6.dp
private val BarGap = 5.dp
private val BarRadius = 3.dp
