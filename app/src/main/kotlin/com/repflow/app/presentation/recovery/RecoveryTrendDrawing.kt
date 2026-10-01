package com.repflow.app.presentation.recovery

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/*
 * The `Canvas` half of `3d`'s trend chart ([RecoveryTrendCard]): the
 * prototype's geometry (`RepFlow.dc.html:4408-4434`) as fractions of its
 * 104-tall plot - values from y 84 (0) to y 8 (5), futsal dots at y 97 - with
 * each day at the centre of one equal column.
 */

/** A value's height as a fraction of the plot: the prototype's `py`, 84 at 0 and 8 at 5. */
private fun yFraction(value: Float): Float = (PLOT_ZERO_Y - value / SCALE_TOP * (PLOT_ZERO_Y - PLOT_TOP_Y)) / PLOT_HEIGHT_UNITS

internal fun yFraction(value: Int): Float = yFraction(value.toFloat())

internal fun DrawScope.columnCentre(
    index: Int,
    count: Int,
): Float = size.width * (index + HALF) / count

internal fun DrawScope.drawGrid(color: Color) {
    GRID_VALUES.forEach { value ->
        val y = size.height * yFraction(value)
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
}

internal fun DrawScope.drawSelection(
    selected: Int,
    count: Int,
    accent: Color,
) {
    val x = columnCentre(selected, count)
    drawLine(
        color = accent.copy(alpha = SELECTION_ALPHA),
        start = Offset(x, size.height * SELECTION_TOP_Y / PLOT_HEIGHT_UNITS),
        end = Offset(x, size.height * SELECTION_BOTTOM_Y / PLOT_HEIGHT_UNITS),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(SelectionDash.toPx(), SelectionDash.toPx())),
    )
}

/**
 * One series. A day without a check-in breaks the line rather than being
 * bridged; a day with no neighbour to join gets a small dot, so it is not
 * invisible.
 */
internal fun DrawScope.drawSeries(
    days: List<RecoveryTrendDay>,
    valueOf: (RecoveryTrendDay) -> Int?,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    val path = Path()
    days.forEachIndexed { index, day ->
        val value = valueOf(day) ?: return@forEachIndexed
        val point = Offset(columnCentre(index, days.size), size.height * yFraction(value))
        val previous = days.getOrNull(index - 1)?.let(valueOf)
        val next = days.getOrNull(index + 1)?.let(valueOf)
        if (previous == null) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        if (previous == null && next == null) drawCircle(color, radius = LonePointRadius.toPx(), center = point)
    }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(EnergyDash.toPx(), EnergyDash.toPx())) else null
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
}

internal fun DrawScope.drawSelectedPoints(
    days: List<RecoveryTrendDay>,
    selected: Int,
    sleepColor: Color,
    energyColor: Color,
    background: Color,
) {
    val day = days[selected]
    val x = columnCentre(selected, days.size)
    day.energy?.let {
        val centre = Offset(x, size.height * yFraction(it))
        drawCircle(background, radius = EnergyDotRadius.toPx(), center = centre)
        drawCircle(energyColor, radius = EnergyDotRadius.toPx(), center = centre, style = Stroke(EnergyDotStroke.toPx()))
    }
    day.sleepQuality?.let {
        drawCircle(sleepColor, radius = SleepDotRadius.toPx(), center = Offset(x, size.height * yFraction(it)))
    }
}

internal fun DrawScope.drawFutsalDays(
    days: List<RecoveryTrendDay>,
    color: Color,
) {
    days.forEachIndexed { index, day ->
        if (day.hadFutsal) {
            drawCircle(
                color,
                radius = FutsalDotRadius.toPx(),
                center = Offset(columnCentre(index, days.size), size.height * FUTSAL_Y / PLOT_HEIGHT_UNITS),
            )
        }
    }
}

private const val SCALE_TOP_VALUE = 5
private const val GRID_MIDDLE_VALUE = 3
internal val GRID_VALUES = listOf(SCALE_TOP_VALUE, GRID_MIDDLE_VALUE, 1)
private const val SCALE_TOP = 5f
private const val PLOT_HEIGHT_UNITS = 104f
private const val PLOT_ZERO_Y = 84f
private const val PLOT_TOP_Y = 8f
private const val FUTSAL_Y = 97f
private const val SELECTION_TOP_Y = 4f
private const val SELECTION_BOTTOM_Y = 88f
private const val HALF = 0.5f
private const val SELECTION_ALPHA = 0.45f
private val EnergyDash = 4.dp
private val SelectionDash = 3.dp
private val SleepDotRadius = 4.5.dp
private val EnergyDotRadius = 3.5.dp
private val EnergyDotStroke = 1.6.dp
private val LonePointRadius = 2.5.dp
private val FutsalDotRadius = 2.dp
