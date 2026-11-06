package com.repflow.app.presentation.home

import com.repflow.app.presentation.backup.LastBackupLabel
import com.repflow.app.presentation.backup.lastBackupLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The rule behind Home's backup line (boards `10n`, `10o`, decision N5), pure. */
class HomeBackupReminderTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.parse("2026-08-31")

    private fun daysAgo(
        days: Long,
        at: LocalTime = LocalTime.NOON,
    ): Instant =
        today
            .minusDays(days)
            .atTime(at)
            .atZone(zone)
            .toInstant()

    @Suppress("LongParameterList")
    private fun rule(
        reminderOn: Boolean = true,
        workoutRunning: Boolean = false,
        hasTrainingData: Boolean = true,
        lastBackupAt: Instant? = null,
        firstWorkoutEndedAt: Instant? = null,
        today: LocalDate = this.today,
        zone: ZoneId = this.zone,
    ): HomeBackupReminder? =
        backupReminderOf(
            reminderOn = reminderOn,
            workoutRunning = workoutRunning,
            hasTrainingData = hasTrainingData,
            lastBackupAt = lastBackupAt,
            firstWorkoutEndedAt = firstWorkoutEndedAt,
            today = today,
            zone = zone,
        )

    @Test
    fun `a backup under 21 days old shows nothing`() {
        assertNull(rule(lastBackupAt = daysAgo(20)))
    }

    @Test
    fun `a backup 21 or more days old shows its age`() {
        assertEquals(HomeBackupReminder.LastBackup(21), rule(lastBackupAt = daysAgo(21)))
        assertEquals(HomeBackupReminder.LastBackup(90), rule(lastBackupAt = daysAgo(90)))
    }

    @Test
    fun `a backup at 23_50 is 21 days old at 00_10 twenty-one dates later`() {
        val backup = daysAgo(21, at = LocalTime.of(23, 50))
        val check = today.atTime(0, 10).atZone(zone).toInstant()

        assertEquals(HomeBackupReminder.LastBackup(21), rule(lastBackupAt = backup, today = check.atZone(zone).toLocalDate()))
    }

    @Test
    fun `one minute short of the 21st date shows nothing, though it is nearly 21 days`() {
        val backup = daysAgo(21, at = LocalTime.of(0, 0))
        val check =
            today
                .minusDays(1)
                .atTime(23, 59)
                .atZone(zone)
                .toInstant()

        assertNull(rule(lastBackupAt = backup, today = check.atZone(zone).toLocalDate()))
    }

    @Test
    fun `the dates are the zone's own`() {
        val backup = Instant.parse("2026-08-11T01:00:00Z")
        val saoPaulo = ZoneId.of("America/Sao_Paulo")

        // 20 dates ago in UTC, but 22:00 the evening before in Sao Paulo, which makes it 21.
        assertNull(rule(lastBackupAt = backup, zone = zone))
        assertEquals(HomeBackupReminder.LastBackup(21), rule(lastBackupAt = backup, zone = saoPaulo))
    }

    @Test
    fun `Home and the Backup screen count the same days`() {
        val times = listOf(LocalTime.MIN, LocalTime.NOON, LocalTime.of(23, 59))
        listOf(21L, 22L, 40L, 365L).forEach { days ->
            times.forEach { at ->
                val backup = daysAgo(days, at)
                val now = today.atTime(LocalTime.of(0, 1)).atZone(zone).toInstant()
                val label = lastBackupLabel(backup, now, zone) as LastBackupLabel.DaysAgo

                assertEquals(HomeBackupReminder.LastBackup(label.days), rule(lastBackupAt = backup))
            }
        }
    }

    @Test
    fun `a backup date after today, from a clock moved back, shows nothing`() {
        assertNull(rule(lastBackupAt = today.plusDays(30).atStartOfDay(zone).toInstant()))
    }

    @Test
    fun `the switch off shows nothing, with either line due`() {
        assertNull(rule(reminderOn = false, lastBackupAt = daysAgo(30)))
        assertNull(rule(reminderOn = false, firstWorkoutEndedAt = daysAgo(30)))
    }

    @Test
    fun `a running workout shows nothing, with either line due`() {
        assertNull(rule(workoutRunning = true, lastBackupAt = daysAgo(30)))
        assertNull(rule(workoutRunning = true, firstWorkoutEndedAt = daysAgo(30)))
    }

    @Test
    fun `no training data shows nothing, with either line due`() {
        assertNull(rule(hasTrainingData = false, lastBackupAt = daysAgo(30)))
        assertNull(rule(hasTrainingData = false, firstWorkoutEndedAt = daysAgo(30)))
    }

    @Test
    fun `no backup counts from the first finished workout`() {
        assertNull(rule(firstWorkoutEndedAt = daysAgo(20)))
        assertEquals(HomeBackupReminder.NoBackupYet, rule(firstWorkoutEndedAt = daysAgo(21)))
        assertEquals(HomeBackupReminder.NoBackupYet, rule(firstWorkoutEndedAt = daysAgo(200)))
    }

    @Test
    fun `no backup and no finished workout shows nothing`() {
        assertNull(rule())
    }

    @Test
    fun `a backup wins over an old first workout, whichever way its age falls`() {
        assertEquals(HomeBackupReminder.LastBackup(25), rule(lastBackupAt = daysAgo(25), firstWorkoutEndedAt = daysAgo(100)))
        assertNull(rule(lastBackupAt = daysAgo(3), firstWorkoutEndedAt = daysAgo(100)))
    }
}
