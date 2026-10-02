package com.repflow.app.presentation.workout

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes

/**
 * What happens when a rest ends, from Settings' two independent switches
 * (remediation-1 CP14): the notification - posted on the `rest_timer` channel
 * with that channel's sound, when the permission allows - and an explicit
 * buzz through the system vibrator. Neither depends on the other, and no
 * channel's vibration is relied on.
 */
data class RestAlertPlan(
    val postNotification: Boolean,
    val vibrate: Boolean,
)

/**
 * The four combinations: notification on + vibrate on -> both; notification
 * on + vibrate off -> the notification (with its sound) only; notification off
 * + vibrate on -> the buzz only; both off -> nothing. Whether a planned
 * notification is actually posted is still the permission's call.
 */
fun restAlertPlan(
    notificationEnabled: Boolean,
    vibrateEnabled: Boolean,
): RestAlertPlan = RestAlertPlan(postNotification = notificationEnabled, vibrate = vibrateEnabled)

/**
 * Whether the workout should ask for `POST_NOTIFICATIONS` now (remediation-1
 * CP14): only while the Notification switch is on - never while it is off,
 * and never before it has loaded ([notificationEnabled] `null`) - and only on
 * Android 13+, where the permission exists, when it is not already granted.
 */
fun shouldRequestNotificationPermission(
    notificationEnabled: Boolean?,
    sdkInt: Int,
    isPermissionGranted: Boolean,
): Boolean = notificationEnabled == true && sdkInt >= Build.VERSION_CODES.TIRAMISU && !isPermissionGranted

/**
 * Whether to offer the exact-alarm explanation (functional review J9): only on
 * Android 12+, where the grant exists, while exact alarms are not allowed, and
 * only once - a refusal is respected, and the alert keeps working, inexactly,
 * without it.
 */
fun shouldOfferExactAlarmPrompt(
    sdkInt: Int,
    canScheduleExactAlarms: Boolean,
    alreadyPrompted: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms && !alreadyPrompted

/**
 * The usage the rest-end buzz carries: notification usage, so a backgrounded
 * app's vibration is not dropped and the system's own silent-mode and
 * notification-intensity settings still apply. API 33+ takes it as a
 * [VibrationAttributes] usage; API 28-32 as an [AudioAttributes] usage - the
 * two constants differ in value.
 */
@SuppressLint("InlinedApi") // a compile-time constant, only ever used on API 33+ by the branch below
fun restAlertVibrationUsage(sdkInt: Int): Int =
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        VibrationAttributes.USAGE_NOTIFICATION
    } else {
        AudioAttributes.USAGE_NOTIFICATION
    }

/**
 * The rest-end buzz, behind a seam so a test can record it (remediation-1
 * CP14). [usage] is [restAlertVibrationUsage]'s value for the running SDK.
 * Production binds [SystemRestAlertVibrator].
 */
fun interface RestAlertVibrator {
    fun vibrate(usage: Int)
}
