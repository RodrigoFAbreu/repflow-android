package com.repflow.app.presentation.recovery

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/*
 * `3d`'s trend card (`RepFlow.dc.html:2103-2156`, script `:4408-4434`): sleep
 * as a solid accent line and energy dashed, over a 0-5 axis labelled 5 / 3 /
 * 1, with futsal days marked on the baseline; a readout of the selected day
 * above; tap any day to select it. Drawn with Compose `Canvas` - no charting
 * dependency (plan CP13 item 4).
 *
 * Geometry is the prototype's own, as fractions of its 104-tall plot: values
 * run from y 84 (0) to y 8 (5), futsal dots sit at y 97. Each day is one
 * equal column, its point at the column's centre, so the tap targets, the
 * points and the tick labels share one grid.
 */

@Composable
internal fun RecoveryTrendCard(
    trend: RecoveryTrend,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable(trend.days.last().date) { mutableIntStateOf(trend.defaultSelectedIndex) }
    RepFlowCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = CardPadding, top = CardPadding, end = CardPadding, bottom = CardBottomPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepFlowSectionLabel(text = stringResource(R.string.recovery_history_chart_title), modifier = Modifier.weight(1f))
            if (trend.averageSleep != null && trend.averageEnergy != null) {
                Text(
                    text =
                        stringResource(
                            R.string.recovery_history_chart_average,
                            trend.averageSleep.toPlainString(),
                            trend.averageEnergy.toPlainString(),
                        ),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                )
            }
        }
        if (trend.isEmpty) {
            Text(
                text = stringResource(R.string.recovery_history_chart_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.padding(vertical = RepFlowSpacing.gapLg),
            )
        } else {
            SelectedDayReadout(
                day = trend.days[selected],
                today = today,
                modifier = Modifier.padding(top = RepFlowSpacing.gapLg, bottom = RepFlowSpacing.gapSm),
            )
            TrendPlot(trend = trend, today = today, selected = selected, onSelect = { selected = it })
            TickLabels(trend)
            ChartFooter()
        }
    }
}

/** The selected day: its label, `Sleep 4/5`, `Energy 3/5`, and the futsal mark (`:2109-2116`). */
@Composable
private fun SelectedDayReadout(
    day: RecoveryTrendDay,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(scheme.onSurface.copy(alpha = READOUT_FILL_ALPHA), ReadoutShape)
                .padding(horizontal = RepFlowSpacing.gapMd, vertical = RepFlowSpacing.gapSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Text(
            text = recoveryDayLabel(day.date, today),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.widthIn(min = ReadoutDayMinWidth),
        )
        if (day.sleepQuality != null && day.energy != null) {
            ReadoutValue(stringResource(R.string.recovery_history_chart_sleep), day.sleepQuality, scheme.primary, SleepSwatchHeight)
            ReadoutValue(stringResource(R.string.recovery_history_chart_energy), day.energy, energyColor(), EnergySwatchHeight)
        } else {
            Text(
                text = stringResource(R.string.recovery_history_chart_no_check_in),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(scheme),
            )
        }
        if (day.hadFutsal) {
            Icon(
                painter = painterResource(RepFlowIcons.soccerBall),
                contentDescription = stringResource(R.string.recovery_history_chart_futsal_day),
                tint = scheme.primary,
                modifier = Modifier.size(FutsalGlyphSize),
            )
        }
    }
}

@Composable
private fun ReadoutValue(
    label: String,
    value: Int,
    swatch: Color,
    swatchHeight: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs)) {
        Box(Modifier.size(width = SwatchWidth, height = swatchHeight).background(swatch))
        Text(
            text =
                buildAnnotatedString {
                    append("$label ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(value.toString()) }
                    append(SCALE_SUFFIX)
                },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = READOUT_TEXT_ALPHA),
        )
    }
}

@Composable
private fun TrendPlot(
    trend: RecoveryTrend,
    today: LocalDate,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val axisColor = repFlowSecondaryTextColor(scheme)
    val gridColor = scheme.onSurface.copy(alpha = GRID_ALPHA)
    val sleepColor = scheme.primary
    val energyColor = energyColor()
    val background = scheme.surface
    val description = stringResource(R.string.recovery_history_chart_description)
    Row(horizontalArrangement = Arrangement.spacedBy(AxisGap)) {
        Box(Modifier.width(AxisWidth).height(PlotHeight)) {
            GRID_VALUES.forEach { value ->
                Text(
                    text = value.toString(),
                    style = axisTextStyle(),
                    color = axisColor,
                    textAlign = TextAlign.End,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = PlotHeight * yFraction(value) - AxisLabelHalfHeight),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(PlotHeight)
                    .semantics { contentDescription = description },
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawGrid(gridColor)
                drawSelection(selected, trend.days.size, sleepColor)
                drawSeries(trend.days, { it.energy }, energyColor, EnergyStroke.toPx(), dashed = true)
                drawSeries(trend.days, { it.sleepQuality }, sleepColor, SleepStroke.toPx(), dashed = false)
                drawSelectedPoints(trend.days, selected, sleepColor, energyColor, background)
                drawFutsalDays(trend.days, sleepColor)
            }
            DayTargets(trend = trend, today = today, selected = selected, onSelect = onSelect)
        }
    }
}

/** One equal-width column per day over the plot, each selecting its day. */
@Composable
private fun DayTargets(
    trend: RecoveryTrend,
    today: LocalDate,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val values = trend.days.map { dayDescription(it) }
    Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
        trend.days.forEachIndexed { index, day ->
            val label = stringResource(R.string.recovery_history_chart_day_description, recoveryDayLabel(day.date, today), values[index])
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = index == selected, role = Role.RadioButton, onClick = { onSelect(index) })
                    .semantics { contentDescription = label },
            )
        }
    }
}

@Composable
private fun dayDescription(day: RecoveryTrendDay): String {
    val values =
        if (day.sleepQuality != null && day.energy != null) {
            stringResource(R.string.recovery_history_chart_day_values, day.sleepQuality, day.energy)
        } else {
            stringResource(R.string.recovery_history_chart_no_check_in)
        }
    return if (day.hadFutsal) "$values, ${stringResource(R.string.recovery_history_chart_futsal_day)}" else values
}

/** Day-of-month under every third day and the last (`chartTicks`, `:4419`). */
@Composable
private fun TickLabels(trend: RecoveryTrend) {
    Row(Modifier.fillMaxWidth().padding(top = TickTopGap).clearAndSetSemantics { }) {
        Spacer(Modifier.width(AxisWidth + AxisGap))
        trend.days.forEachIndexed { index, day ->
            Text(
                text = if (index % TICK_EVERY == 0 || index == trend.days.lastIndex) day.date.dayOfMonth.toString() else "",
                style = axisTextStyle(),
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** "Tap any day for its values" and the futsal legend (`:2152-2155`). */
@Composable
private fun ChartFooter() {
    val color = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = RepFlowSpacing.gapSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = stringResource(R.string.recovery_history_chart_hint), style = style, color = color, modifier = Modifier.weight(1f))
        Box(Modifier.size(LegendDotSize).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(
            text = stringResource(R.string.recovery_history_chart_futsal_legend),
            style = style,
            color = color,
            modifier = Modifier.padding(start = LegendGap),
        )
    }
}

@Composable
private fun axisTextStyle() = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

/** `#b2b6ca` in the prototype: a neutral that stays distinct from the accent in both themes. */
@Composable
private fun energyColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = ENERGY_ALPHA)

private const val TICK_EVERY = 3

/** `<b>4</b>/5`: the scale's top, after the bold value. */
private const val SCALE_SUFFIX = "/5"
private const val GRID_ALPHA = 0.10f
private const val ENERGY_ALPHA = 0.7f
private const val READOUT_FILL_ALPHA = 0.05f
private const val READOUT_TEXT_ALPHA = 0.8f

private val CardPadding = 14.dp
private val CardBottomPadding = 10.dp
private val ReadoutShape = RoundedCornerShape(9.dp)
private val ReadoutDayMinWidth = 52.dp
private val SwatchWidth = 10.dp
private val SleepSwatchHeight = 2.5.dp
private val EnergySwatchHeight = 2.dp
private val FutsalGlyphSize = 15.dp
private val PlotHeight = 104.dp
private val AxisWidth = 16.dp
private val AxisGap = 6.dp
private val AxisLabelHalfHeight = 6.dp
private val TickTopGap = 2.dp
private val SleepStroke = 2.4.dp
private val EnergyStroke = 1.8.dp
private val LegendDotSize = 6.dp
private val LegendGap = 5.dp
