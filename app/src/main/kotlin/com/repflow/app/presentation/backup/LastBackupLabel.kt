package com.repflow.app.presentation.backup

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** How long ago the last backup was, in whole calendar days - the hero's `Last backup <when>` (`8c`). */
internal sealed interface LastBackupLabel {
    data object Today : LastBackupLabel

    data object Yesterday : LastBackupLabel

    data class DaysAgo(
        val days: Int,
    ) : LastBackupLabel
}

/**
 * [lastBackupAt] against [now], counted in calendar days of [zone] (a backup at
 * 23:50 is `yesterday` ten minutes after midnight). A timestamp in the future,
 * from a clock that moved back, reads as `Today`.
 */
internal fun lastBackupLabel(
    lastBackupAt: Instant,
    now: Instant,
    zone: ZoneId,
): LastBackupLabel {
    val days = ChronoUnit.DAYS.between(lastBackupAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate())
    return when {
        days <= 0 -> LastBackupLabel.Today
        days == 1L -> LastBackupLabel.Yesterday
        else -> LastBackupLabel.DaysAgo(days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
