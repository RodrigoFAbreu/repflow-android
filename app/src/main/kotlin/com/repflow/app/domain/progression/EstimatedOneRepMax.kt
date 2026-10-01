package com.repflow.app.domain.progression

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Estimated one-repetition maximum for one set, by the **Brzycki** formula:
 *
 *     1RM = load × 36 / (37 − reps)
 *
 * Chosen because it reproduces the design's own sample (`RepFlow.dc.html`
 * `1c`: best set `82.5 × 7`, `Est. 1RM 99 kg` - 82.5 × 36 / 30 = 99), and a
 * single rep returns the load itself.
 *
 * The estimate is only defined for 1 to [MAX_REPS] repetitions: rep-max
 * formulas lose their meaning on long sets (Brzycki's denominator reaches zero
 * at 37), and a light high-rep set would otherwise out-score the heavy sets
 * the estimate exists to describe. A set outside that range has no estimate
 * (`null`), as does a negative load.
 *
 * Remediation-1 CP15 (plan item 2), consumed by the Progress tab's `Est. 1RM`
 * metric. Pure Kotlin: no Android import (`LayerBoundaryTest`).
 */
object EstimatedOneRepMax {
    /** The longest set, in repetitions, an estimate is made from. */
    const val MAX_REPS = 12

    private const val NUMERATOR = 36
    private const val DENOMINATOR_BASE = 37
    private const val SCALE = 4

    /** The Brzycki estimate for [load] kilograms lifted [reps] times, or `null` outside 1..[MAX_REPS] reps or below 0 kg. */
    fun brzycki(
        load: BigDecimal,
        reps: Int,
    ): BigDecimal? {
        if (reps !in 1..MAX_REPS || load.signum() < 0) return null
        return load.multiply(BigDecimal(NUMERATOR)).divide(BigDecimal(DENOMINATOR_BASE - reps), SCALE, RoundingMode.HALF_UP)
    }
}
