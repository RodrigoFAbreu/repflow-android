package com.repflow.app.application.progress

import java.time.Instant
import java.time.ZoneId

/**
 * The Progress chart's range pills (`5b`: `3m` / `6m` / `All`, default `All`).
 * By date, not by point count (Q7): the chart draws every session inside it.
 * Replaces the parent's 12-session window.
 *
 * @property months how many calendar months back the range reaches; `null`
 *   for [ALL], which has no bound.
 */
enum class ProgressRange(
    private val months: Long?,
) {
    THREE_MONTHS(THREE),
    SIX_MONTHS(SIX),
    ALL(null),
    ;

    /**
     * The **exclusive** lower bound as of [now], in [zone]: the same local
     * date and time [months] calendar months earlier, clamped to the last day
     * of a shorter target month (31 May minus 3 months is 29 or 28 February).
     * A session exactly at the bound is outside; `null` for [ALL].
     */
    fun startsAfter(
        now: Instant,
        zone: ZoneId,
    ): Instant? = months?.let { now.atZone(zone).minusMonths(it).toInstant() }
}

private const val THREE = 3L
private const val SIX = 6L
