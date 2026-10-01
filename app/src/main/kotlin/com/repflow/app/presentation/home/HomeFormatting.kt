package com.repflow.app.presentation.home

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Home's plain-value formatting (remediation-1 CP5), kept out of the
 * composables so a JVM test can pin it.
 */

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val HALF_MINUTE_SECONDS = 30L
private const val DAYS_SHOWN_BY_WEEKDAY = 6L

/**
 * The resume card's elapsed time - the prototype's `mm:ss` (`nResumeMeta`),
 * with hours in front once a session passes one: `4:05`, then `1:01:00`
 * rather than `61:00`. Negative input (a clock set back) reads as zero.
 */
internal fun elapsedLabel(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val hours = total / SECONDS_PER_HOUR
    val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val secs = total % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

/** A finished workout's length in whole minutes, rounded to nearest - the `Last workout` card's `61 min`. */
internal fun durationMinutes(
    startedAt: Instant,
    endedAt: Instant,
): Long {
    val seconds = Duration.between(startedAt, endedAt).seconds.coerceAtLeast(0L)
    return (seconds + HALF_MINUTE_SECONDS) / SECONDS_PER_MINUTE
}

/** When the last workout happened, relative to today, as the card names it. */
internal sealed interface WorkoutDay {
    data object Today : WorkoutDay

    data object Yesterday : WorkoutDay

    /** Within the last week: the design's `Mon`. */
    data class Weekday(
        val dayOfWeek: DayOfWeek,
    ) : WorkoutDay

    /** Older than that, where a weekday alone would be ambiguous: a date. */
    data class OnDate(
        val date: LocalDate,
    ) : WorkoutDay
}

internal fun workoutDayOf(
    endedAt: Instant,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): WorkoutDay {
    val day = endedAt.atZone(zone).toLocalDate()
    return when {
        day == today -> WorkoutDay.Today
        day == today.minusDays(1) -> WorkoutDay.Yesterday
        day.isAfter(today.minusDays(DAYS_SHOWN_BY_WEEKDAY + 1)) && !day.isAfter(today) -> WorkoutDay.Weekday(day.dayOfWeek)
        else -> WorkoutDay.OnDate(day)
    }
}
