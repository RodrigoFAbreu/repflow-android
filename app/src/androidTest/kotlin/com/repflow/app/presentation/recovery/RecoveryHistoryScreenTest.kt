package com.repflow.app.presentation.recovery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.FutsalSessionId
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Stateless Compose coverage for [RecoveryHistoryScreen] on `3d`'s
 * composition (remediation-1 CP13 items 3 and 4; new, as the checkpoint's
 * grep found no existing test): the trend chart's readout and day selection,
 * then the entries and futsal sessions below it.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryHistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 8, 11)
    private val at = Instant.parse("2026-08-01T00:00:00Z")

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun entry(
        date: LocalDate,
        sleep: Int,
        energy: Int,
        doms: Int,
        played: Boolean = false,
    ): RecoveryEntry =
        (
            RecoveryEntry.create(
                id = RecoveryEntryId("r-$date"),
                date = date,
                sleepQuality = sleep,
                energy = energy,
                legDoms = doms,
                heelStiffness = 0,
                painWhileWalking = 0,
                heavyLegs = 0,
                futsalInPrevious24h = played,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = at,
                updatedAt = at,
            ) as DomainResult.Success
        ).value

    private fun futsal(date: LocalDate): FutsalSession =
        (
            FutsalSession.create(
                id = FutsalSessionId("f-$date"),
                date = date,
                durationMinutes = 50,
                sessionRpe = 8.0,
                createdAt = at,
                updatedAt = at,
            ) as DomainResult.Success
        ).value

    private fun setScreen(
        uiState: RecoveryHistoryUiState,
        onBackClick: () -> Unit = {},
    ) {
        composeRule.setContent { RecoveryHistoryScreen(uiState = uiState, onBackClick = onBackClick) }
    }

    private fun withData(): RecoveryHistoryUiState =
        RecoveryHistoryUiState(
            isLoading = false,
            today = today,
            recoveryEntries =
                listOf(
                    entry(today, sleep = 4, energy = 3, doms = 2, played = true),
                    entry(today.minusDays(2), sleep = 2, energy = 1, doms = 4),
                ),
            futsalSessions = listOf(futsal(today.minusDays(1))),
        )

    @Test
    fun theReadoutOpensOnTheLatestCheckInAndTappingADaySelectsIt() {
        setScreen(withData())

        composeRule.onNodeWithText("${string(R.string.recovery_history_chart_sleep)} 4/5").assertIsDisplayed()
        composeRule.onNodeWithText("${string(R.string.recovery_history_chart_energy)} 3/5").assertIsDisplayed()

        composeRule
            .onNodeWithContentDescription(
                string(
                    R.string.recovery_history_chart_day_description,
                    "9 Aug",
                    string(R.string.recovery_history_chart_day_values, 2, 1),
                ),
            ).performClick()

        composeRule.onNodeWithText("${string(R.string.recovery_history_chart_sleep)} 2/5").assertIsDisplayed()
        composeRule.onNodeWithText("${string(R.string.recovery_history_chart_energy)} 1/5").assertIsDisplayed()
    }

    @Test
    fun aDayWithoutACheckInReadsSoAndShowsItsFutsal() {
        setScreen(withData())

        composeRule
            .onNodeWithContentDescription(
                string(
                    R.string.recovery_history_chart_day_description,
                    "10 Aug",
                    string(R.string.recovery_history_chart_no_check_in) + ", " + string(R.string.recovery_history_chart_futsal_day),
                ),
            ).performClick()

        composeRule.onNodeWithText(string(R.string.recovery_history_chart_no_check_in)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.recovery_history_chart_futsal_day)).assertIsDisplayed()
    }

    @Test
    fun theChartAveragesTheCheckInsInTheWindow() {
        setScreen(withData())

        composeRule.onNodeWithText(string(R.string.recovery_history_chart_average, "3.0", "2.0")).assertIsDisplayed()
    }

    @Test
    fun entriesAndFutsalSessionsAreListedUnderTheChart() {
        setScreen(withData())

        composeRule.onNodeWithText(string(R.string.recovery_history_recovery_row, 4, 3, 2)).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(string(R.string.recovery_history_played_content_description))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.recovery_history_futsal_row, 50, "8")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.recovery_history_futsal_load, "400")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withNothingRecordedTheEmptyStateShows() {
        setScreen(RecoveryHistoryUiState(isLoading = false, today = today))

        composeRule.onNodeWithText(string(R.string.recovery_history_empty)).assertIsDisplayed()
    }

    @Test
    fun backInvokesOnBackClick() {
        var back = false
        setScreen(withData(), onBackClick = { back = true })

        composeRule.onNodeWithContentDescription(string(R.string.repflow_back_content_description)).performClick()

        assertTrue(back)
    }
}
