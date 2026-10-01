package com.repflow.app.presentation.recovery

import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/*
 * The read model behind `3d`'s trend chart (remediation-1 CP13): the last
 * fourteen calendar days ending today, one point per day, with the day's
 * sleep and energy when a check-in exists and a futsal mark when a futsal
 * session was recorded for it. Pure, so the window, the gaps and the
 * averages are pinned on the JVM.
 */

/** `3d`'s window: "Sleep & energy · 14 days". */
const val RECOVERY_TREND_DAYS = 14

/**
 * One day on the chart.
 *
 * @property sleepQuality null when the day has no check-in; the lines break
 *   there rather than drawing a value nobody entered.
 * @property hadFutsal a futsal session is recorded for [date].
 */
data class RecoveryTrendDay(
    val date: LocalDate,
    val sleepQuality: Int?,
    val energy: Int?,
    val hadFutsal: Boolean,
) {
    val hasCheckIn: Boolean get() = sleepQuality != null && energy != null
}

/**
 * @property days oldest first, exactly [RECOVERY_TREND_DAYS] of them, the
 *   last being the window's end.
 * @property averageSleep the mean over the days with a check-in, or null when
 *   there is none - `3d`'s `avg 3.6 / 3.1`.
 */
data class RecoveryTrend(
    val days: List<RecoveryTrendDay>,
    val averageSleep: BigDecimal?,
    val averageEnergy: BigDecimal?,
) {
    /** Nothing to draw: no check-in and no futsal anywhere in the window. */
    val isEmpty: Boolean get() = days.none { it.hasCheckIn || it.hadFutsal }

    /**
     * The day the readout opens on: the most recent day with a check-in, or
     * the window's last day when there is none (`3d`: the last day).
     */
    val defaultSelectedIndex: Int
        get() = days.indexOfLast { it.hasCheckIn }.takeIf { it >= 0 } ?: days.lastIndex
}

/** The [RECOVERY_TREND_DAYS] days ending on [endDate], from every entry and session. */
fun recoveryTrendOf(
    entries: List<RecoveryEntry>,
    futsalSessions: List<FutsalSession>,
    endDate: LocalDate,
): RecoveryTrend {
    val entriesByDate = entries.associateBy { it.date }
    val futsalDates = futsalSessions.mapTo(HashSet()) { it.date }
    val days =
        (RECOVERY_TREND_DAYS - 1 downTo 0).map { back ->
            val date = endDate.minusDays(back.toLong())
            val entry = entriesByDate[date]
            RecoveryTrendDay(
                date = date,
                sleepQuality = entry?.sleepQuality,
                energy = entry?.energy,
                hadFutsal = date in futsalDates,
            )
        }
    val checkedIn = days.filter { it.hasCheckIn }
    return RecoveryTrend(
        days = days,
        averageSleep = checkedIn.averageOf { it.sleepQuality },
        averageEnergy = checkedIn.averageOf { it.energy },
    )
}

/** One decimal place, half up: `3.6`, never `3.5714`. */
private fun List<RecoveryTrendDay>.averageOf(value: (RecoveryTrendDay) -> Int?): BigDecimal? {
    if (isEmpty()) return null
    val sum = sumOf { requireNotNull(value(it)) }
    return BigDecimal(sum).divide(BigDecimal(size), 1, RoundingMode.HALF_UP)
}
