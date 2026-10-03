package com.repflow.app.presentation.progress

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * The Progress chart's plain maths (remediation-1-remediation-1 CP3, `5b`),
 * kept out of the composables so a JVM test can pin it: which points carry a
 * date label so none overlap, which point a touch lands on, and how a value, a
 * date and a delta read. The y axis is [ChartScale].
 */

/**
 * Which points carry a date label under the chart, so no two overlap
 * (functional review GX-I2). The prototype labelled every third point because
 * it never drew more than twelve; `All` now draws every session, so the stride
 * comes from what fits: the smallest `n` such that `n` points' spacing is at
 * least the label's width plus [gapPx]. Labels sit at points `0, n, 2n ...`;
 * the first and the last point are always labelled, and when the last
 * stride-aligned label would sit closer to the last point's than the gap
 * allows, it is the one dropped.
 *
 * @param plotWidthPx the distance from the first point to the last.
 * @param labelWidthPx the widest plotted label.
 */
internal fun xLabelIndices(
    pointCount: Int,
    plotWidthPx: Float,
    labelWidthPx: Float,
    gapPx: Float,
): List<Int> {
    if (pointCount <= 0) return emptyList()
    if (pointCount == 1) return listOf(0)
    val last = pointCount - 1
    val spacing = plotWidthPx / last
    val needed = labelWidthPx + gapPx
    val stride = if (spacing <= 0f) last else ceil(needed / spacing).toInt().coerceIn(1, last)
    val aligned = (0 until last step stride).toMutableList()
    val nearest = aligned.last()
    if (nearest != 0 && (last - nearest) * spacing < needed) aligned.removeAt(aligned.lastIndex)
    return aligned + last
}

/** The point a touch at [x] lands on: the nearest one, however many there are. */
internal fun nearestPointIndex(
    x: Float,
    plotLeftPx: Float,
    plotWidthPx: Float,
    pointCount: Int,
): Int {
    if (pointCount <= 1 || plotWidthPx <= 0f) return 0
    val spacing = plotWidthPx / (pointCount - 1)
    return ((x - plotLeftPx) / spacing).roundToInt().coerceIn(0, pointCount - 1)
}

/** A point's x position: evenly spaced across the plot, a lone point in the middle. */
internal fun pointX(
    index: Int,
    pointCount: Int,
    plotLeftPx: Float,
    plotWidthPx: Float,
): Float = if (pointCount <= 1) plotLeftPx + plotWidthPx / 2f else plotLeftPx + plotWidthPx * index / (pointCount - 1)

/** `82.5`, `80`, `2,310` - grouped as History groups its volume, never `80.0` or `8.25E+1`. */
internal fun plainNumber(
    value: BigDecimal,
    locale: Locale,
): String =
    NumberFormat
        .getNumberInstance(locale)
        .apply { maximumFractionDigits = MAX_FRACTION_DIGITS }
        .format(value.stripTrailingZeros())

/** The delta always carries its sign, so its direction never rests on colour: `+10`, `+0`, `−2.5` (U+2212, as the done screen writes it). */
internal fun signedNumber(
    delta: BigDecimal,
    locale: Locale,
): String = if (delta.signum() < 0) "$MINUS${plainNumber(delta.negate(), locale)}" else "+${plainNumber(delta, locale)}"

private const val MAX_FRACTION_DIGITS = 6
private const val MINUS = "−"

/** `12 Aug`; `12 Aug 2025` when the date is not in the year of the series' latest point, so a long `All` never reads ambiguously. */
internal fun dateLabel(
    at: Instant,
    zone: ZoneId,
    locale: Locale,
    withYear: Boolean,
): String = DateTimeFormatter.ofPattern(if (withYear) DATE_WITH_YEAR else DATE, locale).format(at.atZone(zone))

/**
 * The span under the headline value (`8b`): `5 Jul – 2 Oct 2026`. The year
 * stands once, on the end, when both ends are in the same year, and on both
 * ends when they are not; a single date (one session, or both on one day)
 * stands alone with its year.
 */
internal fun spanDates(
    first: Instant,
    last: Instant,
    zone: ZoneId,
    locale: Locale,
): String {
    val start = first.atZone(zone)
    val end = last.atZone(zone)
    val endText = DateTimeFormatter.ofPattern(DATE_WITH_YEAR, locale).format(end)
    return when {
        start.toLocalDate() == end.toLocalDate() -> endText
        start.year == end.year -> "${DateTimeFormatter.ofPattern(DATE, locale).format(start)}$SPAN_DASH$endText"
        else -> "${DateTimeFormatter.ofPattern(DATE_WITH_YEAR, locale).format(start)}$SPAN_DASH$endText"
    }
}

private const val SPAN_DASH = " – "
private const val DATE = "d MMM"
private const val DATE_WITH_YEAR = "d MMM yyyy"
