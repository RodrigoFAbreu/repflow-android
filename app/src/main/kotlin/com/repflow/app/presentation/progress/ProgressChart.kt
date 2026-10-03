package com.repflow.app.presentation.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal
import kotlin.math.roundToInt

/*
 * `5b`'s line chart on Compose `Canvas` (no charting dependency): a 96 tall
 * plot with three y labels and gridlines at the maximum, midpoint and minimum,
 * a 2.4 line over a soft area, the selected point as a dot with a dashed
 * cursor, and date labels under it. The whole chart is one accessibility node
 * with a spoken summary; the selection is read from the strip above it.
 *
 * Every session in the range is drawn, however many, so the scrubber picks the
 * nearest point to the touch rather than a fixed-width strip per point, and
 * the date labels are placed by [xLabelIndices] from their measured width.
 */

/** The test tag of the chart's one node. */
internal const val PROGRESS_CHART_TAG = "progress-chart"

/** The test tag prefix of a drawn date label; the point's index follows. */
internal const val PROGRESS_X_LABEL_TAG = "progress-x-label-"

@Composable
internal fun ProgressChart(
    values: List<BigDecimal>,
    dateLabels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val locale = currentProgressLocale()
    val axisStyle =
        MaterialTheme.typography.labelSmall.copy(fontSize = AxisFontSize, letterSpacing = 0.sp, fontWeight = FontWeight.Normal)
    val axisColor = repFlowSecondaryTextColor(scheme)
    val scale = remember(values) { requireNotNull(ChartScale.of(values)) }
    val labelWidthPx =
        remember(dateLabels, axisStyle) {
            dateLabels.maxOf { textMeasurer.measure(it, axisStyle, softWrap = false, maxLines = 1).size.width }
        }
    val gridLabels = remember(scale, locale) { scale.gridValues.map { plainNumber(it, locale) } }
    val lineColor = scheme.primary
    val dotColor = repFlowSelectedPillColors(scheme).label
    val gridColor = scheme.onSurface.copy(alpha = GRID_ALPHA)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.toFloat()
        val axisWidthPx = with(density) { (YAxisWidth + YAxisGap).toPx() }
        val inset = labelWidthPx / 2f
        val plotLeft = axisWidthPx + inset
        val plotWidth = (widthPx - plotLeft - inset).coerceAtLeast(0f)
        val gapPx = with(density) { MinLabelGap.toPx() }
        val shown =
            remember(values.size, plotWidth, labelWidthPx, gapPx) { xLabelIndices(values.size, plotWidth, labelWidthPx.toFloat(), gapPx) }

        // The gesture coroutines outlive a recomposition (they restart only when a key changes), so
        // they must read the latest callback, not the one captured when the first touch arrived.
        val currentOnSelect by rememberUpdatedState(onSelect)

        Column {
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PlotHeight)
                        .testTag(PROGRESS_CHART_TAG)
                        .semantics { contentDescription = description }
                        .pointerInput(values.size, plotLeft, plotWidth) {
                            detectTapGestures { currentOnSelect(nearestPointIndex(it.x, plotLeft, plotWidth, values.size)) }
                        }.pointerInput(values.size, plotLeft, plotWidth) {
                            detectHorizontalDragGestures { change, _ ->
                                currentOnSelect(nearestPointIndex(change.position.x, plotLeft, plotWidth, values.size))
                            }
                        },
            ) {
                drawChart(
                    ChartDrawing(
                        values = values,
                        scale = scale,
                        selectedIndex = selectedIndex,
                        geometry =
                            ChartGeometry(
                                plotLeft = plotLeft,
                                plotWidth = plotWidth,
                                axisWidth = axisWidthPx - YAxisGap.toPx(),
                                gridLeft = axisWidthPx,
                            ),
                        colors = ChartColors(line = lineColor, dot = dotColor, grid = gridColor, axis = axisColor),
                        axisText = AxisText(gridLabels = gridLabels, textMeasurer = textMeasurer, style = axisStyle),
                    ),
                )
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = LabelTopGap).height(LabelRowHeight)) {
                val labelWidth = with(density) { labelWidthPx.toDp() }
                shown.forEach { index ->
                    val x = pointX(index, values.size, plotLeft, plotWidth) - labelWidthPx / 2f
                    Text(
                        text = dateLabels[index],
                        style = axisStyle,
                        color = axisColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        modifier =
                            Modifier
                                .offset { IntOffset(x.roundToInt(), 0) }
                                .width(labelWidth)
                                .clearAndSetSemantics { testTag = "$PROGRESS_X_LABEL_TAG$index" },
                    )
                }
            }
        }
    }
}

private class ChartColors(
    val line: Color,
    val dot: Color,
    val grid: Color,
    val axis: Color,
)

private class ChartGeometry(
    val plotLeft: Float,
    val plotWidth: Float,
    val axisWidth: Float,
    val gridLeft: Float,
)

private class AxisText(
    val gridLabels: List<String>,
    val textMeasurer: TextMeasurer,
    val style: TextStyle,
)

private class ChartDrawing(
    val values: List<BigDecimal>,
    val scale: ChartScale,
    val selectedIndex: Int,
    val geometry: ChartGeometry,
    val colors: ChartColors,
    val axisText: AxisText,
)

private fun DrawScope.drawChart(chart: ChartDrawing) {
    val top = PlotTopInset.toPx()
    val bottom = size.height - PlotBottomInset.toPx()

    fun yOf(fraction: Float) = bottom - fraction * (bottom - top)

    chart.scale.gridFractions().forEachIndexed { index, fraction ->
        val y = yOf(fraction)
        drawLine(chart.colors.grid, Offset(chart.geometry.gridLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        val label =
            chart.axisText.textMeasurer.measure(
                chart.axisText.gridLabels[index],
                chart.axisText.style,
                softWrap = false,
                maxLines = 1,
            )
        drawText(
            label,
            color = chart.colors.axis,
            topLeft = Offset(chart.geometry.axisWidth - label.size.width, y - label.size.height / 2f),
        )
    }

    val points =
        chart.values.mapIndexed { index, value ->
            Offset(pointX(index, chart.values.size, chart.geometry.plotLeft, chart.geometry.plotWidth), yOf(chart.scale.fractionOf(value)))
        }
    val selected = points[chart.selectedIndex.coerceIn(0, points.lastIndex)]
    if (points.size > 1) {
        val line = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
        val area =
            Path().apply {
                addPath(line)
                lineTo(points.last().x, bottom)
                lineTo(points.first().x, bottom)
                close()
            }
        drawPath(area, chart.colors.line.copy(alpha = AREA_ALPHA))
        drawLine(
            chart.colors.line.copy(alpha = CURSOR_ALPHA),
            Offset(selected.x, CursorTopInset.toPx()),
            Offset(selected.x, size.height - CursorBottomInset.toPx()),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(CURSOR_DASH.dp.toPx(), CURSOR_DASH.dp.toPx())),
        )
        drawPath(
            line,
            chart.colors.line,
            style = Stroke(width = LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    drawCircle(chart.colors.dot, radius = DotRadius.toPx(), center = selected)
}

private const val GRID_ALPHA = 0.10f
private const val AREA_ALPHA = 0.12f
private const val CURSOR_ALPHA = 0.45f
private const val CURSOR_DASH = 3f

private val AxisFontSize = 10.sp
private val YAxisWidth = 34.dp
private val YAxisGap = 6.dp

/** The design's 96. */
private val PlotHeight = 96.dp
private val PlotTopInset = 8.dp
private val PlotBottomInset = 8.dp
private val CursorTopInset = 4.dp
private val CursorBottomInset = 8.dp
private val LineWidth = 2.4.dp
private val DotRadius = 4.5.dp
private val LabelTopGap = 2.dp
private val LabelRowHeight = 16.dp

/** The least space left between two drawn date labels (review GX-I2). */
internal val MinLabelGap = 8.dp
