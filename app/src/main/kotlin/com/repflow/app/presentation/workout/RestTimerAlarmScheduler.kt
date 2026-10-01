package com.repflow.app.presentation.workout

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.time.Instant

/**
 * Schedules/cancels the exact alarm that triggers [RestTimerExpiredReceiver]
 * at a rest timer's absolute end timestamp, so the OS notification still
 * fires even if the app process is backgrounded or killed. Presentation-only;
 * the domain/application layers know nothing about `AlarmManager`.
 *
 * The alarm is scheduled for every running rest whatever Settings says, and
 * its intent carries no preference: the receiver reads the switches when it
 * fires (remediation-1 CP14).
 */
object RestTimerAlarmScheduler {
    private const val REQUEST_CODE = 2001

    fun schedule(
        context: Context,
        endAt: Instant,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = pendingIntent(context)
        val canScheduleExact =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt.toEpochMilli(), pendingIntent)
        } else {
            // No SCHEDULE_EXACT_ALARM grant: fall back to an inexact alarm rather than crashing or
            // nagging for a permission that is not essential to the in-app timer itself.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt.toEpochMilli(), pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context))
    }

    /**
     * The currently scheduled alarm's [PendingIntent], or `null` if none is
     * scheduled: the same intent and request code, looked up with
     * `FLAG_NO_CREATE`. Sending it delivers the broadcast to the
     * manifest-declared receiver exactly as the alarm would (remediation-1
     * CP14's receiver-delivery test).
     */
    @VisibleForTesting
    internal fun scheduledPendingIntent(context: Context): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, RestTimerExpiredReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, RestTimerExpiredReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
