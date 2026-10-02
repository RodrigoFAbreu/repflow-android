package com.repflow.app.presentation.workout

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.repflow.app.R
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

/**
 * Remediation-1 CP14 item 3: the real receiver delivers. In the app's own Hilt
 * graph - the manifest-declared [RestTimerExpiredReceiver], its injected
 * [RestTimerExpiryHandler] and the Room-backed `SettingsRepository` - a rest
 * alarm is scheduled, Settings' Notification switch is flipped in the Settings
 * screen, and the scheduled [android.app.PendingIntent] is sent as the alarm
 * would send it. The outcome must follow the switch's value **at delivery**:
 *
 * - off at scheduling, on at delivery -> the notification is posted (a receiver
 *   that does nothing fails here);
 * - on at scheduling, off at delivery -> nothing is posted (the pre-CP14
 *   receiver, which posted whenever permission was granted, fails here).
 *
 * Each case cancels the notification first (the handler test posts the same
 * id), and afterwards cancels the alarm and the notification and restores the
 * switch.
 *
 * Since the self-review the handler alerts only while an active session still
 * has a running rest, so each case first gives the app's own database one -
 * written through a second Room instance on the same file, the app graph
 * having no test hook - and afterwards abandons the session it started, or
 * restores the one it found.
 */
@RunWith(AndroidJUnit4::class)
class RestTimerReceiverDeliveryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val manager: NotificationManager by lazy { composeRule.activity.getSystemService(NotificationManager::class.java) }
    private var originalNotificationSwitch: Boolean? = null
    private lateinit var appDatabase: RepFlowDatabase
    private lateinit var workouts: LocalWorkoutRepository
    private var sessionFound: WorkoutSession? = null
    private var sessionStarted: WorkoutSession? = null

    @Before
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry
                .getInstrumentation()
                .uiAutomation
                .grantRuntimePermission(composeRule.activity.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
        composeRule.onNodeWithContentDescription(string(R.string.home_settings_content_description)).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { switchIs(true) || switchIs(false) }
        originalNotificationSwitch = switchIs(true)
        startARunningRest()
    }

    @After
    fun cleanUp() {
        RestTimerAlarmScheduler.cancel(composeRule.activity)
        manager.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
        originalNotificationSwitch?.let(::setNotificationSwitch)
        if (::appDatabase.isInitialized) {
            runBlocking {
                sessionStarted?.let { started -> workouts.update(started.abandon(Instant.now()).successValue()) }
                sessionFound?.let { found -> workouts.update(found) }
            }
            appDatabase.close()
        }
    }

    private fun startARunningRest() {
        appDatabase = Room.databaseBuilder(composeRule.activity, RepFlowDatabase::class.java, APP_DATABASE_NAME).build()
        workouts =
            LocalWorkoutRepository(
                appDatabase,
                appDatabase.workoutSessionDao(),
                appDatabase.workoutExerciseDao(),
                appDatabase.workoutSetDao(),
            )
        val rest = RestTimer.start(durationSeconds = REST_SECONDS, now = Instant.now())
        runBlocking {
            val found = workouts.findActiveSession()
            if (found == null) {
                val started =
                    WorkoutSession
                        .start(WorkoutSessionId(UUID.randomUUID().toString()), trainingPlanVersionId = null, startedAt = Instant.now())
                        .withStartedRestTimer(rest)
                        .successValue()
                assertTrue(workouts.insert(started) is DomainResult.Success)
                sessionStarted = started
            } else {
                sessionFound = found
                assertTrue(workouts.update(found.withStartedRestTimer(rest).successValue()) is DomainResult.Success)
            }
        }
    }

    private fun DomainResult<WorkoutSession, *>.successValue(): WorkoutSession = (this as DomainResult.Success).value

    @Test
    fun offAtSchedulingOnAtDeliveryPostsTheNotification() {
        setNotificationSwitch(false)
        RestTimerAlarmScheduler.schedule(composeRule.activity, Instant.now().plusSeconds(SCHEDULE_AHEAD_SECONDS))
        setNotificationSwitch(true)

        deliverTheScheduledAlarm()

        assertTrue("expected the rest-timer notification", awaitPosted())
    }

    @Test
    fun onAtSchedulingOffAtDeliveryPostsNothing() {
        setNotificationSwitch(true)
        RestTimerAlarmScheduler.schedule(composeRule.activity, Instant.now().plusSeconds(SCHEDULE_AHEAD_SECONDS))
        setNotificationSwitch(false)

        deliverTheScheduledAlarm()

        assertFalse("expected no rest-timer notification", awaitPosted())
    }

    private fun deliverTheScheduledAlarm() {
        val scheduled = RestTimerAlarmScheduler.scheduledPendingIntent(composeRule.activity)
        assertNotNull("the alarm must be scheduled", scheduled)
        scheduled?.send()
    }

    /** Waits, with a bound, for the notification; `false` if it never came. */
    private fun awaitPosted(): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (isPosted()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return isPosted()
    }

    private fun isPosted(): Boolean =
        manager.activeNotifications.any {
            it.id == RestTimerExpiredReceiver.NOTIFICATION_ID && it.notification.channelId == RestTimerExpiredReceiver.CHANNEL_ID
        }

    /**
     * Flips the switch through the Settings screen if needed, then waits for it to
     * render the stored value. A switch counts only once it is enabled - it is
     * disabled, and reads off, until the stored settings have loaded.
     */
    private fun setNotificationSwitch(on: Boolean) {
        if (!switchIs(on)) {
            composeRule.onNodeWithText(string(R.string.settings_rest_notification)).performScrollTo().performClick()
        }
        composeRule.waitUntil(TIMEOUT_MILLIS) { switchIs(on) }
    }

    private fun switchIs(on: Boolean): Boolean =
        composeRule
            .onAllNodes(hasText(string(R.string.settings_rest_notification)) and isEnabled() and (if (on) isOn() else isOff()))
            .fetchSemanticsNodes()
            .isNotEmpty()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private companion object {
        const val SCHEDULE_AHEAD_SECONDS = 600L
        const val REST_SECONDS = 600

        /** `DatabaseModule.DATABASE_NAME`, which is private to `infrastructure/di`. */
        const val APP_DATABASE_NAME = "repflow.db"
        const val TIMEOUT_MILLIS = 5_000L
        const val POLL_MILLIS = 50L
    }
}
