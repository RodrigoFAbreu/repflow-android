package com.repflow.app.presentation.workout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.repflow.app.R

/**
 * Fires when a scheduled rest timer's absolute end timestamp is reached
 * (via [RestTimerAlarmScheduler]/`AlarmManager`), even if the app is
 * backgrounded or the process has been killed. Posts a notification (with
 * default vibration) only when `POST_NOTIFICATIONS` is granted (Android 13+);
 * otherwise this is a silent no-op, per the "notification behavior when
 * permission is denied" decision in milestone-4-reference.md - the in-app
 * timer itself never depends on this.
 */
@Suppress("MagicNumber")
class RestTimerExpiredReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(context.getString(R.string.workout_active_rest_timer_notification_title))
                .setContentText(context.getString(R.string.workout_active_rest_timer_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.workout_active_rest_timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 1001
    }
}
