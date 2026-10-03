package com.repflow.app.presentation.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.repflow.app.R
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.workout.WorkoutRepository
import javax.inject.Inject

/**
 * What [RestTimerExpiredReceiver] does when a rest ends (remediation-1 CP14),
 * moved out of the receiver so it can be injected and tested.
 *
 * **Settings are read when the alarm fires, not when it was scheduled**: a rest
 * can still be running while the user leaves the workout for Settings, and the
 * scheduled alarm carries no preference. So [onRestEnded] makes one read of
 * the current values, then runs the two halves [restAlertPlan] decides:
 *
 * - **Notification** - only when the switch is on *and* [notificationPermitted]
 *   (the receiver's own `POST_NOTIFICATIONS` check, first, unchanged: denied
 *   means no notification). It posts on the `rest_timer_v2` channel: `IMPORTANCE_HIGH`,
 *   the default notification sound, vibration disabled, so the app's own buzz
 *   is the only one (GF-5). The channel is created if absent (a no-op once it
 *   exists, so the user's changes to it survive) and the old `rest_timer`
 *   channel, whose vibration cannot be changed, is deleted; any customisation
 *   of that old channel resets.
 * - **Vibration** - the explicit one-shot buzz through [vibrator], only when the
 *   Vibrate switch is on, with notification usage ([restAlertVibrationUsage]).
 *   It needs no notification permission, so it fires even when that is denied.
 *
 * **Only a rest that is still running alerts.** Before anything else it reads
 * the active session: no active session, or one with no rest timer, means the
 * rest the alarm was scheduled for has gone - the workout was finished,
 * abandoned from Home's resume card, erased from Settings or replaced by a
 * restore, or its rest was skipped - so nothing is posted and nothing buzzes.
 * Only the workout route cancels the alarm, and it is not on screen for those
 * paths (remediation-1 self-review).
 *
 * Known platform limit (accepted): in vibrate ringer mode Android turns the
 * channel's sound into a fallback vibration, so the phone can buzz with Vibrate
 * off while the notification is on.
 */
class RestTimerExpiryHandler
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val workoutRepository: WorkoutRepository,
        private val vibrator: RestAlertVibrator,
    ) {
        suspend fun onRestEnded(
            context: Context,
            notificationPermitted: Boolean,
        ) {
            if (workoutRepository.findActiveSession()?.restTimer == null) return
            val settings = settingsRepository.get()
            val plan = restAlertPlan(settings.restTimerNotification, settings.restTimerVibrate)
            if (plan.postNotification && notificationPermitted) postNotification(context)
            if (plan.vibrate) vibrator.vibrate(restAlertVibrationUsage(Build.VERSION.SDK_INT))
        }

        private fun postNotification(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.deleteNotificationChannel(RestTimerExpiredReceiver.LEGACY_CHANNEL_ID)
            manager.createNotificationChannel(
                NotificationChannel(
                    RestTimerExpiredReceiver.CHANNEL_ID,
                    context.getString(R.string.workout_active_rest_timer_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { enableVibration(false) },
            )
            val notification =
                NotificationCompat
                    .Builder(context, RestTimerExpiredReceiver.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(context.getString(R.string.workout_active_rest_timer_notification_title))
                    .setContentText(context.getString(R.string.workout_active_rest_timer_notification_text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            @Suppress("MissingPermission") // the caller has already checked POST_NOTIFICATIONS
            manager.notify(RestTimerExpiredReceiver.NOTIFICATION_ID, notification)
        }
    }
