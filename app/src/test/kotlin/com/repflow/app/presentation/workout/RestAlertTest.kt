package com.repflow.app.presentation.workout

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
