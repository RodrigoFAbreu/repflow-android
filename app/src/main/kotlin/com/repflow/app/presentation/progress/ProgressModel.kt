package com.repflow.app.presentation.progress

import com.repflow.app.application.progress.ProgressPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

/*
 * The Progress card's plain values (remediation-1 CP15, `4a`), kept out of the
 * composables so a JVM test can pin them: each bar's height, which bars carry
 * a month label, and how a value and a delta read.
 */

/**
 * Each bar's height as a fraction of the plot, by the prototype's own rule
 * (`RepFlow.dc.html:3767`): the window's lowest value sits at 14%, its highest
 * at 96%, and no bar is shorter than 6%. A flat series draws every bar at 14%.
 */
internal fun barHeightFractions(values: List<BigDecimal>): List<Float> {
    val min = values.minOrNull() ?: return emptyList()
    val max = values.max()
    val span = max.subtract(min)
    return values.map { value ->
        val normalised = if (span.signum() == 0) 0.0 else value.subtract(min).divide(span, NORMALISE_SCALE, RoundingMode.HALF_UP).toDouble()
        val percent = maxOf(MIN_BAR_PERCENT, Math.round(normalised * BAR_RANGE_PERCENT).toInt() + BAR_FLOOR_PERCENT)
        percent / PERCENT
    }
}

/**
 * The month under each bar, or `null` for none: `4a` labels a bar where a new
 * month starts (`May · · Jun · · Jul …`, `PMONTHS` at `:3123`), so the first
 * bar and each bar whose session falls in a later month than the one before.
 */
internal fun barMonthLabels(
    points: List<ProgressPoint>,
    zone: ZoneId,
): List<YearMonth?> {
    val months = points.map { YearMonth.from(it.startedAt.atZone(zone)) }
    return months.mapIndexed { index, month -> if (index == 0 || month != months[index - 1]) month else null }
}

/** `82.5`, `80`, `2310` - never `80.0` or `8.25E+1`. */
internal fun plainNumber(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

/** The delta always carries its sign, so its direction never rests on colour: `+10`, `+0`, `−2.5` (U+2212, as the done screen writes it). */
internal fun signedNumber(delta: BigDecimal): String =
    if (delta.signum() < 0) "$MINUS${plainNumber(delta.negate())}" else "+${plainNumber(delta)}"

private const val MINUS = "−"
private const val NORMALISE_SCALE = 6
private const val MIN_BAR_PERCENT = 6
private const val BAR_RANGE_PERCENT = 82
private const val BAR_FLOOR_PERCENT = 14
private const val PERCENT = 100f
