package com.repflow.app.presentation.home

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val REMINDER_AFTER_DAYS = 21

/** What Home's quiet backup line says (boards `10n` and `10o`, decision N5). */
sealed interface HomeBackupReminder {
    /** `Last backup N days ago`. */
    data class LastBackup(
        val days: Int,
    ) : HomeBackupReminder

    /** `No backup yet`: there has never been a backup and the first finished workout is old. */
    data object NoBackupYet : HomeBackupReminder
}

/**
 * Whether Home shows the backup line, and what it says. `null` means no line.
 *
 * It never shows with the switch off, while a workout runs, or on a phone with no training data: Erase
 * keeps [lastBackupAt], so an emptied phone would otherwise show an old date on the first-run Home.
 * Otherwise it shows once [lastBackupAt] is 21 or more days old. With no backup at all, the count starts
 * at [firstWorkoutEndedAt], so someone new is never nagged, and a phone with no finished workout gets no
 * line. A date after [today], from a clock that moved back, gives no line.
 *
 * Days are calendar days of [zone], the same count as `lastBackupLabel`, so Home and Backup never
 * disagree about "N days ago".
 */
@Suppress("LongParameterList")
internal fun backupReminderOf(
    reminderOn: Boolean,
    workoutRunning: Boolean,
    hasTrainingData: Boolean,
    lastBackupAt: Instant?,
    firstWorkoutEndedAt: Instant?,
    today: LocalDate,
    zone: ZoneId,
): HomeBackupReminder? {
    val canShow = reminderOn && !workoutRunning && hasTrainingData
    val since = lastBackupAt ?: firstWorkoutEndedAt
    if (!canShow || since == null) return null
    val days = ChronoUnit.DAYS.between(since.atZone(zone).toLocalDate(), today)
    return when {
        days < REMINDER_AFTER_DAYS -> null
        lastBackupAt == null -> HomeBackupReminder.NoBackupYet
        else -> HomeBackupReminder.LastBackup(days.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
