package com.repflow.app.presentation.workout

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires when a scheduled rest timer's absolute end timestamp is reached (via
 * [RestTimerAlarmScheduler]/`AlarmManager`), even if the app is backgrounded
 * or its process has been killed.
 *
 * Since remediation-1 CP14 its work is [RestTimerExpiryHandler]'s, which reads
 * Settings' Notification and Vibrate switches **when the alarm fires**, and
 * gates the two independently:
 *
 * - the notification is posted only when the Notification switch is on and
 *   `POST_NOTIFICATIONS` is granted (Android 13+) - this permission check is
 *   unchanged and comes first: denied means no notification, the
 *   "notification behavior when permission is denied" decision in
 *   milestone-4-reference.md. The notification plays the `rest_timer`
 *   channel's sound; that channel does not vibrate;
 * - the phone buzzes, through the system vibrator, only when the Vibrate
 *   switch is on - and since that needs no notification permission, it buzzes
 *   even when the permission is denied.
 *
 * With both switches off it does nothing, and nothing happens either when no
 * active session still has a running rest (the workout ended, or its rest was
 * skipped, away from the workout screen). The in-app timer never depends on
 * this receiver.
 *
 * `onReceive` hands off to the handler on [Dispatchers.IO] through
 * [goAsync] and [launchRestAlert], finishing the pending result whatever
 * happens. It uses `Dispatchers.IO` directly because the `@IoDispatcher`
 * qualifier lives in `infrastructure/di`, which `presentation` may not import.
 */
@AndroidEntryPoint
class RestTimerExpiredReceiver : BroadcastReceiver() {
    @Inject
    lateinit var expiryHandler: RestTimerExpiryHandler

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val notificationPermitted = hasNotificationPermission(appContext)
        launchRestAlert(onDone = pendingResult::finish) {
            expiryHandler.onRestEnded(appContext, notificationPermitted)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "rest_timer"
        const val NOTIFICATION_ID = 1001
    }
}

private const val LOG_TAG = "RestTimerExpired"

/**
 * Where a failed rest alert ends (implementation-review revision 1's O1): the
 * alert runs in a background coroutine with no screen to report to, so a
 * failure - in practice a storage error reading the session or the switches
 * when the alarm fires - is logged and the alert skipped, instead of crashing
 * the backgrounded process. This is the root coroutine's failure sink, not a
 * `catch` around the suspend work: cancellation never reaches it. The
 * `SQLiteException` family cannot be named here, as `presentation` may not
 * import `android.database`.
 */
private val restAlertFailureHandler =
    CoroutineExceptionHandler { _, failure -> Log.w(LOG_TAG, "Rest alert skipped", failure) }

/**
 * Runs [alert] on [Dispatchers.IO], calling [onDone] when it ends - completed
 * or failed - and sending a failure to [restAlertFailureHandler].
 */
internal fun launchRestAlert(
    onDone: () -> Unit,
    alert: suspend () -> Unit,
): Job =
    CoroutineScope(Dispatchers.IO + restAlertFailureHandler).launch {
        try {
            alert()
        } finally {
            onDone()
        }
    }
