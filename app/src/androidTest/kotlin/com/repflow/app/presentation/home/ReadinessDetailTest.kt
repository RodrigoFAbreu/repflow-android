package com.repflow.app.presentation.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * The readiness sheet's content (remediation-1 CP4 item 4), rendered without
 * its modal host - Home hosts it from CP5, which owns the open/close path.
 */
@RunWith(AndroidJUnit4::class)
class ReadinessDetailTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun show(
        readiness: ReadinessScore,
        onClose: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ReadinessDetail(readiness = readiness, onClose = onClose)
            }
        }
    }

    @Test
    fun showsTheScoreItsBandWordTheDriverSentenceAndEveryFactor() {
        // DOMS 3 and heavy legs 4 flagged, everything else at its best: 80, Ready.
        val readiness = readiness(legDoms = 3, heavyLegs = 4)
        show(readiness)

        composeRule.onNodeWithText(readiness.score.toString(), substring = false).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.readiness_band_ready)).assertIsDisplayed()
        composeRule.onNodeWithText("Driven by leg doms 3/5, heavy legs 4/5.").assertIsDisplayed()
        listOf("Sleep quality", "Energy", "Leg DOMS", "Heavy legs", "Heel stiffness", "Pain while walking").forEach {
            composeRule.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        val flagged = string(R.string.readiness_factor_flagged)
        composeRule.onNodeWithText("3/5 · $flagged").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("×1.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.readiness_sheet_gate_note)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun closeCallsBack() {
        var closed = 0
        show(readiness(), onClose = { closed++ })

        composeRule.onNodeWithText(string(R.string.readiness_sheet_close)).performClick()

        assertEquals(1, closed)
    }

    private fun readiness(
        legDoms: Int = 0,
        heavyLegs: Int = 0,
    ): ReadinessScore {
        val at = Instant.parse("2026-08-11T07:00:00Z")
        val entry =
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-1"),
                date = LocalDate.parse("2026-08-11"),
                sleepQuality = 5,
                energy = 5,
                legDoms = legDoms,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = heavyLegs,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = at,
                updatedAt = at,
            )
        return ReadinessScore.of((entry as DomainResult.Success).value)
    }
}
