package com.repflow.app.presentation.workout

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
 * Functional review round 2, P-1: composing the workout while the rest has
 * already ended - going back from `Why >`, or `Resume` from Home - must not
 * schedule the OS alarm again, whatever path composes it. A past-due exact
 * alarm fires again, within about 5 seconds (Android's alarm minimum), and alerts a
 * second time.
 */
@RunWith(AndroidJUnit4::class)
class RestAlarmEffectTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-01-01T00:01:00Z")
    private val scheduled = mutableListOf<Instant>()
    private var cancels = 0

    private fun setContent(endAt: () -> Instant?) {
        composeRule.setContent {
            RestAlarmEffect(
                restEndAt = endAt(),
                schedule = { scheduled += it },
                cancel = { cancels++ },
                now = { now },
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aRestThatHasEndedIsNeverScheduled() {
        setContent { now.minusSeconds(5) }

        assertEquals(emptyList<Instant>(), scheduled)
    }

    @Test
    fun aRestEndingExactlyNowIsNeverScheduled() {
        setContent { now }

        assertEquals(emptyList<Instant>(), scheduled)
    }

    @Test
    fun aRunningRestIsScheduledForItsEnd() {
        val end = now.plusSeconds(30)
        setContent { end }

        assertEquals(listOf(end), scheduled)
    }

    @Test
    fun noRestCancelsTheAlarm() {
        setContent { null }

        assertEquals(1, cancels)
        assertEquals(emptyList<Instant>(), scheduled)
    }

    /** Implementation review round 6, I-1: the pending alarm is for the old end, so it is brought forward, once. */
    @Test
    fun anAdjustmentThatCutsARunningRestShortFiresTheAlertOnce() {
        val end = now.plusSeconds(10)
        var endAt by mutableStateOf<Instant?>(end)
        setContent { endAt }
        assertEquals(listOf(end), scheduled)

        val newEnd = now.minusSeconds(5)
        endAt = newEnd
        composeRule.waitForIdle()

        assertEquals(listOf(end, newEnd), scheduled)
        assertEquals(0, cancels)
    }

    @Test
    fun anAdjustmentOfARestThatHadAlreadyEndedInThisVisitSchedulesNothing() {
        val end = now.minusSeconds(5)
        var endAt by mutableStateOf<Instant?>(end)
        setContent { endAt }

        endAt = end.minusSeconds(15)
        composeRule.waitForIdle()

        assertEquals(emptyList<Instant>(), scheduled)
    }
}
