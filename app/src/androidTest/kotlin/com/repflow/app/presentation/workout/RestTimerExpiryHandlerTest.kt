package com.repflow.app.presentation.workout

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.database.sqlite.SQLiteDiskIOException
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.repflow.app.R
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.data.backup.LocalTrainingDataRepository
import com.repflow.app.data.settings.LocalSettingsRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.SETTINGS_SEED_CALLBACK
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Remediation-1 CP14 item 3: what happens when a rest ends, per Settings'
 * Notification and Vibrate switches, through [RestTimerExpiryHandler] with the
 * real Room-backed settings and a recording [RestAlertVibrator].
 *
 * For every combination of the two switches, with the notification permission
 * granted and denied, it asserts whether the buzz fired (and with the
 * notification usage for this device's SDK), whether the notification was
 * posted, and that a posted one is on `rest_timer` - not vibrating, with a
 * sound. It runs once without the channel and once with it pre-created, as on
 * an upgraded install, which must survive. And it changes each switch after
 * the alarm is scheduled and before the rest ends, both ways, and asserts the
 * outcome follows the new value: the switches are read when the alarm fires.
 *
 * Each case starts with an active session whose rest is running, as when the
 * alarm was scheduled; the self-review cases end that session or its rest
 * first and assert that nothing alerts.
 */
@RunWith(AndroidJUnit4::class)
class RestTimerExpiryHandlerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val vibrator = RecordingVibrator()
    private lateinit var database: RepFlowDatabase
    private lateinit var settings: LocalSettingsRepository
    private lateinit var workouts: LocalWorkoutRepository
    private lateinit var handler: RestTimerExpiryHandler

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        database =
            Room
                .inMemoryDatabaseBuilder(context, RepFlowDatabase::class.java)
                .addCallback(SETTINGS_SEED_CALLBACK)
                .build()
        settings = LocalSettingsRepository(database)
        workouts =
            LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao())
        handler = RestTimerExpiryHandler(settings, workouts, vibrator)
        manager.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
        runBlocking { workouts.insert(activeSessionWithRest()) }
    }

    @After
    fun tearDown() {
        RestTimerAlarmScheduler.cancel(context)
        manager.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
        database.close()
    }

    @Test
    fun everyCombinationWithoutTheChannel() {
        manager.deleteNotificationChannel(RestTimerExpiredReceiver.CHANNEL_ID)
        assertEveryCombination()
    }

    @Test
    fun everyCombinationWithTheChannelPreCreatedAsOnAnUpgradedInstall() {
        manager.createNotificationChannel(
            NotificationChannel(
                RestTimerExpiredReceiver.CHANNEL_ID,
                context.getString(R.string.workout_active_rest_timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        assertEveryCombination()
        assertNotNull(
            "the existing channel must never be deleted",
            manager.getNotificationChannel(RestTimerExpiredReceiver.CHANNEL_ID),
        )
    }

    @Test
    fun notificationTurnedOffAfterSchedulingPostsNothing() {
        assertSwitchChangedAfterScheduling(from = settingsWith(notification = true, vibrate = false)) {
            it.copy(restTimerNotification = false)
        }
        assertPosted(false)
    }

    @Test
    fun notificationTurnedOnAfterSchedulingPosts() {
        assertSwitchChangedAfterScheduling(from = settingsWith(notification = false, vibrate = false)) {
            it.copy(restTimerNotification = true)
        }
        assertPosted(true)
    }

    @Test
    fun vibrateTurnedOffAfterSchedulingDoesNotBuzz() {
        assertSwitchChangedAfterScheduling(from = settingsWith(notification = false, vibrate = true)) {
            it.copy(restTimerVibrate = false)
        }
        assertEquals(emptyList<Int>(), vibrator.usages)
    }

    @Test
    fun vibrateTurnedOnAfterSchedulingBuzzes() {
        assertSwitchChangedAfterScheduling(from = settingsWith(notification = false, vibrate = false)) {
            it.copy(restTimerVibrate = true)
        }
        assertEquals(listOf(expectedUsage()), vibrator.usages)
    }

    /**
     * Self-review: a rest that is no longer running never alerts, whatever the
     * switches say - the session was abandoned (Home's resume card), erased,
     * or had its rest skipped while the workout screen, the only place that
     * cancels the alarm, was not shown.
     */
    @Test
    fun anAbandonedSessionsRestDoesNotAlert() {
        endTheActiveSession { it.abandon(Instant.now()) }
        assertNoAlertWithEverySwitchOn()
    }

    @Test
    fun aCompletedSessionsRestDoesNotAlert() {
        endTheActiveSession { it.complete(Instant.now()) }
        assertNoAlertWithEverySwitchOn()
    }

    @Test
    fun aSkippedRestDoesNotAlert() {
        endTheActiveSession { it.withClearedRestTimer() }
        assertNoAlertWithEverySwitchOn()
    }

    @Test
    fun erasedDataDoesNotAlert() {
        runBlocking { LocalTrainingDataRepository(database).clearTrainingData() }
        assertNoAlertWithEverySwitchOn()
    }

    /**
     * Implementation-review revision 1's O1: a storage failure while reading
     * the session when the alarm fires reaches the receiver's coroutine, which
     * skips the alert and still finishes the pending broadcast - it does not
     * crash the backgrounded process (an uncaught failure on that IO thread
     * would kill this test's process too).
     */
    @Test
    fun aFailedSessionReadInTheReceiversCoroutineSkipsTheAlertAndStillFinishes() {
        val failing =
            object : WorkoutRepository by workouts {
                override suspend fun findActiveSession(): WorkoutSession? = throw SQLiteDiskIOException("disk I/O error")
            }
        assertTheReceiversCoroutineSkipsTheAlert(RestTimerExpiryHandler(settings, failing, vibrator))
    }

    /** O1's second read: the switches themselves cannot be read when the rest ends. */
    @Test
    fun aFailedSettingsReadInTheReceiversCoroutineSkipsTheAlertAndStillFinishes() {
        val failing =
            object : SettingsRepository by settings {
                override suspend fun get(): AppSettings = throw SQLiteDiskIOException("disk I/O error")
            }
        assertTheReceiversCoroutineSkipsTheAlert(RestTimerExpiryHandler(failing, workouts, vibrator))
    }

    private fun assertTheReceiversCoroutineSkipsTheAlert(failingHandler: RestTimerExpiryHandler) {
        runBlocking { settings.update { settingsWith(notification = true, vibrate = true) } }
        var finished = false

        val job = launchRestAlert(onDone = { finished = true }) { failingHandler.onRestEnded(context, notificationPermitted = true) }
        runBlocking { job.join() }

        assertTrue("the pending broadcast must still be finished", finished)
        assertTrue("the alert's coroutine ended in failure, handled", job.isCancelled)
        assertEquals(emptyList<Int>(), vibrator.usages)
        assertPosted(false)
    }

    private fun endTheActiveSession(change: (WorkoutSession) -> DomainResult<WorkoutSession, *>) {
        runBlocking {
            val active = checkNotNull(workouts.findActiveSession())
            val changed = (change(active) as DomainResult.Success).value
            assertTrue(workouts.update(changed) is DomainResult.Success)
        }
    }

    private fun assertNoAlertWithEverySwitchOn() {
        runBlocking {
            settings.update { settingsWith(notification = true, vibrate = true) }
            handler.onRestEnded(context, notificationPermitted = true)
        }
        assertEquals(emptyList<Int>(), vibrator.usages)
        assertPosted(false)
    }

    private fun activeSessionWithRest(): WorkoutSession {
        val started = WorkoutSession.start(WorkoutSessionId("rest-alert-session"), trainingPlanVersionId = null, startedAt = Instant.now())
        val rest = RestTimer.start(durationSeconds = REST_SECONDS, now = Instant.now())
        return (started.withStartedRestTimer(rest) as DomainResult.Success).value
    }

    private fun assertSwitchChangedAfterScheduling(
        from: AppSettings,
        change: (AppSettings) -> AppSettings,
    ) {
        runBlocking {
            settings.update { from }
            RestTimerAlarmScheduler.schedule(context, Instant.now().plusSeconds(SCHEDULE_AHEAD_SECONDS))
            settings.update(change)
            handler.onRestEnded(context, notificationPermitted = true)
        }
    }

    /** B7: the real canceller removes a posted "Rest done" notification, as finishing or abandoning a workout does. */
    @Test
    fun theRealCancellerClearsAPostedRestDoneNotification() {
        runBlocking {
            settings.update { settingsWith(notification = true, vibrate = false) }
            handler.onRestEnded(context, notificationPermitted = true)
        }
        assertPosted(true)

        SystemRestNotificationCanceller(context).cancel()

        assertTrue("expected the notification to be gone", awaitPosted(false))
    }

    private fun assertEveryCombination() {
        for (notification in listOf(true, false)) {
            for (vibrate in listOf(true, false)) {
                for (permitted in listOf(true, false)) {
                    val case = "notification=$notification vibrate=$vibrate permitted=$permitted"
                    manager.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
                    awaitPosted(false)
                    vibrator.usages.clear()

                    runBlocking {
                        settings.update { settingsWith(notification, vibrate) }
                        handler.onRestEnded(context, notificationPermitted = permitted)
                    }

                    assertEquals(case, if (vibrate) listOf(expectedUsage()) else emptyList<Int>(), vibrator.usages)
                    assertPosted(notification && permitted, case)
                    if (notification && permitted) assertChannelIsTodays(case)
                }
            }
        }
    }

    private fun assertChannelIsTodays(case: String) {
        val channel = manager.getNotificationChannel(RestTimerExpiredReceiver.CHANNEL_ID)
        assertNotNull(case, channel)
        assertFalse("$case: the channel must not vibrate", channel.shouldVibrate())
        assertNotNull("$case: the channel keeps its sound", channel.sound)
        val posted = manager.activeNotifications.single { it.id == RestTimerExpiredReceiver.NOTIFICATION_ID }
        assertEquals(case, RestTimerExpiredReceiver.CHANNEL_ID, posted.notification.channelId)
    }

    private fun assertPosted(
        expected: Boolean,
        case: String = "",
    ) {
        if (expected) {
            assertTrue("$case: expected the notification", awaitPosted(true))
        } else {
            Thread.sleep(SETTLE_MILLIS)
            assertFalse("$case: expected no notification", isPosted())
        }
    }

    /** Polls, with a bound, until the notification's presence is [present]; returns whether it got there. */
    private fun awaitPosted(present: Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (isPosted() == present) return true
            Thread.sleep(POLL_MILLIS)
        }
        return isPosted() == present
    }

    private fun isPosted(): Boolean = manager.activeNotifications.any { it.id == RestTimerExpiredReceiver.NOTIFICATION_ID }

    private fun settingsWith(
        notification: Boolean,
        vibrate: Boolean,
    ) = AppSettings.DEFAULT.copy(restTimerNotification = notification, restTimerVibrate = vibrate)

    private fun expectedUsage(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes.USAGE_NOTIFICATION
        } else {
            AudioAttributes.USAGE_NOTIFICATION
        }

    /** Records the usage each buzz carried, instead of buzzing. */
    class RecordingVibrator : RestAlertVibrator {
        val usages = mutableListOf<Int>()

        override fun vibrate(usage: Int) {
            usages += usage
        }
    }

    private companion object {
        const val SCHEDULE_AHEAD_SECONDS = 600L
        const val REST_SECONDS = 90
        const val TIMEOUT_MILLIS = 3_000L
        const val POLL_MILLIS = 50L
        const val SETTLE_MILLIS = 300L
    }
}
