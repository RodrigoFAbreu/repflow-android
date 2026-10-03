package com.repflow.app.presentation.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
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

/** The words for [label]: `Last backup today`, `yesterday` or `N days ago` - shared by Backup's hero and Settings' row. */
@Composable
internal fun lastBackupTitle(label: LastBackupLabel): String =
    when (label) {
        LastBackupLabel.Today -> stringResource(R.string.backup_last_today)
        LastBackupLabel.Yesterday -> stringResource(R.string.backup_last_yesterday)
        is LastBackupLabel.DaysAgo -> stringResource(R.string.backup_last_days_ago, label.days)
    }
