package com.repflow.app.presentation.workout

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Remediation-1 CP14 item 3: the workout's `POST_NOTIFICATIONS` request follows
 * the Notification switch. With a rest running and the permission not granted:
 * off -> no request; on -> one request on API 33+ and none below; not loaded
 * yet -> none until the switch loads on, then one. Both sides of the API 33
 * line run here on one device through the effect's `sdkInt`.
 */
@RunWith(AndroidJUnit4::class)
class RestTimerPermissionPromptEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val restEnd = Instant.parse("2026-01-01T00:02:00Z")

    private fun requestsFor(
        notificationEnabled: Boolean?,
        sdkInt: Int,
    ): Int {
        var requests = 0
        composeRule.setContent {
            RestTimerPermissionPromptEffect(
                restTimerEndAt = restEnd,
                notificationEnabled = notificationEnabled,
                isPermissionGranted = { false },
                requestPermission = { requests++ },
                sdkInt = sdkInt,
            )
        }
        composeRule.waitForIdle()
        return requests
    }

    @Test
    fun switchOffNeverRequests() {
        assertEquals(0, requestsFor(notificationEnabled = false, sdkInt = Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun switchOnRequestsOnceOnApi33() {
        assertEquals(1, requestsFor(notificationEnabled = true, sdkInt = Build.VERSION_CODES.TIRAMISU))
    }

    @Test
    fun switchOnDoesNotRequestBelowApi33() {
        assertEquals(0, requestsFor(notificationEnabled = true, sdkInt = Build.VERSION_CODES.S_V2))
    }

    @Test
    fun noRequestBeforeTheSwitchLoadsThenOneWhenItLoadsOn() {
        var requests = 0
        var enabled by mutableStateOf<Boolean?>(null)
        composeRule.setContent {
            RestTimerPermissionPromptEffect(
                restTimerEndAt = restEnd,
                notificationEnabled = enabled,
                isPermissionGranted = { false },
                requestPermission = { requests++ },
                sdkInt = Build.VERSION_CODES.TIRAMISU,
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, requests)

        enabled = true

        composeRule.waitForIdle()
        assertEquals(1, requests)
    }

    /** Functional review R2-F-5: `+/-15s` changes the rest's end but must not ask again after a denial. */
    @Test
    fun adjustingARunningRestNeverRequestsAgainButTheNextRestDoes() {
        var requests = 0
        var endAt by mutableStateOf<Instant?>(restEnd)
        composeRule.setContent {
            RestTimerPermissionPromptEffect(
                restTimerEndAt = endAt,
                notificationEnabled = true,
                isPermissionGranted = { false },
                requestPermission = { requests++ },
                sdkInt = Build.VERSION_CODES.TIRAMISU,
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, requests)

        endAt = restEnd.minusSeconds(15)
        composeRule.waitForIdle()
        endAt = restEnd.plusSeconds(15)
        composeRule.waitForIdle()
        assertEquals(1, requests)

        endAt = null
        composeRule.waitForIdle()
        endAt = restEnd
        composeRule.waitForIdle()
        assertEquals(2, requests)
    }
}
