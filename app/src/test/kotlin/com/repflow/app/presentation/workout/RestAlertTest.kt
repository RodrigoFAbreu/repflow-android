package com.repflow.app.presentation.workout

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Remediation-1 CP14: the pure halves of the rest-end alert and the permission prompt. */
class RestAlertTest {
    @Test
    fun `notification and vibrate are gated independently across all four combinations`() {
        assertEquals(RestAlertPlan(postNotification = true, vibrate = true), restAlertPlan(true, true))
        assertEquals(RestAlertPlan(postNotification = true, vibrate = false), restAlertPlan(true, false))
        assertEquals(RestAlertPlan(postNotification = false, vibrate = true), restAlertPlan(false, true))
        assertEquals(RestAlertPlan(postNotification = false, vibrate = false), restAlertPlan(false, false))
    }

    @Test
    fun `no prompt while notification is off or not loaded, whatever the SDK and grant`() {
        for (enabled in listOf(false, null)) {
            for (sdk in listOf(Build.VERSION_CODES.P, Build.VERSION_CODES.S_V2, Build.VERSION_CODES.TIRAMISU, 35)) {
                for (granted in listOf(true, false)) {
                    assertFalse("enabled=$enabled sdk=$sdk granted=$granted", shouldRequestNotificationPermission(enabled, sdk, granted))
                }
            }
        }
    }

    @Test
    fun `the exact alarm explanation is offered once, on Android 12 and later, only while exact alarms are not allowed`() {
        assertTrue(shouldOfferExactAlarmPrompt(Build.VERSION_CODES.S, canScheduleExactAlarms = false, alreadyPrompted = false))
        assertTrue(shouldOfferExactAlarmPrompt(36, canScheduleExactAlarms = false, alreadyPrompted = false))
        // Already allowed, already offered, or no such grant below Android 12: never.
        assertFalse(shouldOfferExactAlarmPrompt(36, canScheduleExactAlarms = true, alreadyPrompted = false))
        assertFalse(shouldOfferExactAlarmPrompt(36, canScheduleExactAlarms = false, alreadyPrompted = true))
        assertFalse(shouldOfferExactAlarmPrompt(Build.VERSION_CODES.R, canScheduleExactAlarms = false, alreadyPrompted = false))
    }

    @Test
    fun `the notification ask is outstanding only until it has been answered, granted or denied`() {
        val tiramisu = Build.VERSION_CODES.TIRAMISU
        assertTrue(isNotificationAskPending(true, tiramisu, isPermissionGranted = false, requestAnswered = false))
        // Denied: still not granted, but answered - the exact-alarm explanation must no longer wait.
        assertFalse(isNotificationAskPending(true, tiramisu, isPermissionGranted = false, requestAnswered = true))
        assertFalse(isNotificationAskPending(true, tiramisu, isPermissionGranted = true, requestAnswered = false))
        assertFalse(isNotificationAskPending(false, tiramisu, isPermissionGranted = false, requestAnswered = false))
        assertFalse(isNotificationAskPending(true, Build.VERSION_CODES.S_V2, isPermissionGranted = false, requestAnswered = false))
    }

    @Test
    fun `a user who denied notifications is still offered the exact alarm explanation once`() {
        val pending = isNotificationAskPending(true, 36, isPermissionGranted = false, requestAnswered = true)
        assertFalse(pending)
        assertTrue(!pending && shouldOfferExactAlarmPrompt(36, canScheduleExactAlarms = false, alreadyPrompted = false))
    }

    @Test
    fun `resume re-arms a rest that is still running and never one that has ended`() {
        val now = Instant.parse("2026-10-02T10:00:00Z")
        assertTrue(shouldRearmRestAlarm(now.plusSeconds(30), now))
        assertFalse(shouldRearmRestAlarm(now, now))
        assertFalse(shouldRearmRestAlarm(now.minusSeconds(1), now))
        assertFalse(shouldRearmRestAlarm(null, now))
    }

    @Test
    fun `with notification on, a prompt only on API 33 and later without the grant`() {
        assertTrue(shouldRequestNotificationPermission(true, Build.VERSION_CODES.TIRAMISU, isPermissionGranted = false))
        assertTrue(shouldRequestNotificationPermission(true, 35, isPermissionGranted = false))
        assertFalse(shouldRequestNotificationPermission(true, Build.VERSION_CODES.TIRAMISU, isPermissionGranted = true))
        assertFalse(shouldRequestNotificationPermission(true, Build.VERSION_CODES.S_V2, isPermissionGranted = false))
        assertFalse(shouldRequestNotificationPermission(true, Build.VERSION_CODES.P, isPermissionGranted = false))
    }

    @Test
    fun `the buzz carries the notification usage constant for the running SDK`() {
        assertEquals(VibrationAttributes.USAGE_NOTIFICATION, restAlertVibrationUsage(Build.VERSION_CODES.TIRAMISU))
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, restAlertVibrationUsage(Build.VERSION_CODES.S_V2))
        assertEquals(AudioAttributes.USAGE_NOTIFICATION, restAlertVibrationUsage(Build.VERSION_CODES.P))
        assertNotEquals(VibrationAttributes.USAGE_NOTIFICATION, AudioAttributes.USAGE_NOTIFICATION)
    }
}
