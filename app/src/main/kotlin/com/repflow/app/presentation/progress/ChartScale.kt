package com.repflow.app.presentation.progress

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The y axis `5b` draws: gridlines and labels at the maximum, the midpoint and
 * the minimum of the plotted values. [fractionOf] gives a value's height as a
 * fraction of the plot, 0 at the bottom and 1 at the top.
 *
 * A flat series (or a single point) has no span to scale by, so it sits on the
 * middle line and carries one label rather than three equal ones.
 */
internal class ChartScale(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    val isFlat: Boolean get() = max.compareTo(min) == 0

    val mid: BigDecimal get() = min.add(max).divide(TWO, MID_SCALE, RoundingMode.HALF_UP)

    /** The gridline values from the top down: three for a span, one for a flat series. */
    val gridValues: List<BigDecimal> get() = if (isFlat) listOf(max) else listOf(max, mid, min)

    fun fractionOf(value: BigDecimal): Float =
        if (isFlat) {
            FLAT_FRACTION
        } else {
            value.subtract(min).divide(max.subtract(min), FRACTION_SCALE, RoundingMode.HALF_UP).toFloat()
        }

    /** A gridline's own height as a fraction of the plot, as [fractionOf] gives it. */
    fun gridFractions(): List<Float> = gridValues.map(::fractionOf)

    companion object {
        /** `null` for no values. */
        fun of(values: List<BigDecimal>): ChartScale? {
            val min = values.minOrNull() ?: return null
            return ChartScale(min, values.max())
        }

        private val TWO = BigDecimal(2)
        private const val MID_SCALE = 6
        private const val FRACTION_SCALE = 6
        private const val FLAT_FRACTION = 0.5f
    }
}
