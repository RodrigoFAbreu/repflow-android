package com.repflow.app.presentation.progression

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.repflow.app.presentation.home.readinessBandLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Stateless coverage of the recommendation screen (remediation-1 CP6): **one
 * state test per `ProgressionResult` case** (plan CP6 "Tests CP6 owes"), each
 * rendered as its own state - its outcome as the title, every reason the
 * policy recorded, and its own primary action - never as an error. The
 * recovery adjustment also links to the readiness sheet. The last tests pin
 * the `Your call` options and the recorded-choice state.
 */
@RunWith(AndroidJUnit4::class)
class ProgressionRecommendationScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun loaded(
        suggested: ProgressionResultUi,
        reasons: List<String>,
        choice: RecommendationChoice? = null,
        readiness: ReadinessScore? = null,
    ) = RecommendationContent.Loaded(
        exerciseName = "Back Squat",
        suggested = suggested,
        reasons = reasons,
        policyVersion = 1,
        choice = choice,
        readiness = readiness,
    )

    private fun setContent(
        state: ProgressionRecommendationUiState,
        onDone: () -> Unit = {},
        onChooseAnother: () -> Unit = {},
        onPick: (ProgressionResultUi) -> Unit = {},
        onKeepSameLoad: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ProgressionRecommendationScreen(
                    uiState = state,
                    onBack = {},
                    onDone = onDone,
                    onChooseAnother = onChooseAnother,
                    onCloseChoices = {},
                    onPick = onPick,
                    onKeepSameLoad = onKeepSameLoad,
                    onRetry = {},
                    onErrorShown = {},
                )
            }
        }
    }

    private fun assertShown(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun anIncreaseRendersItsOutcomeEveryReasonAndItsActions() {
        val reasons = listOf("Every working set reached 8+ reps at average RPE 7.0", "Second reason")
        var done = false
        var kept = false
        setContent(
            ProgressionRecommendationUiState(content = loaded(ProgressionResultUi.INCREASE_LOAD, reasons)),
            onDone = { done = true },
            onKeepSameLoad = { kept = true },
        )

        composeRule.onNodeWithText(string(R.string.progression_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText("BACK SQUAT").assertIsDisplayed()
        assertShown(string(R.string.progression_result_increase_load))
        reasons.forEach(::assertShown)
        assertShown(string(R.string.progression_footnote, 1))
        composeRule.onNodeWithText(string(R.string.progression_go_with_it)).performClick()
        composeRule.onNodeWithText(string(R.string.progression_keep_same)).performClick()
        assertTrue(done)
        assertTrue(kept)
    }

    @Test
    fun aMaintainRendersAsItsOwnStateWithoutKeepTheSameLoad() {
        val reasons = listOf("Performance was within the planned rep range but not clearly at either extreme")
        setContent(ProgressionRecommendationUiState(content = loaded(ProgressionResultUi.MAINTAIN_LOAD, reasons)))

        assertShown(string(R.string.progression_result_maintain_load))
        reasons.forEach(::assertShown)
        composeRule.onNodeWithText(string(R.string.progression_go_with_it)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_pick_another)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_keep_same)).assertDoesNotExist()
    }

    @Test
    fun aReductionRendersAsItsOwnState() {
        val reasons = listOf("Average RPE 9.2 is at or above 9.0, or fewer than half the working sets reached 6 reps")
        setContent(ProgressionRecommendationUiState(content = loaded(ProgressionResultUi.REDUCE_LOAD, reasons)))

        assertShown(string(R.string.progression_result_reduce_load))
        reasons.forEach(::assertShown)
        composeRule.onNodeWithText(string(R.string.progression_go_with_it)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_keep_same)).assertIsDisplayed()
    }

    @Test
    fun notEnoughDataRendersAsItsOwnStateNotAsAnError() {
        val reasons = listOf("Only warm-up sets were recorded - no working sets to evaluate")
        setContent(ProgressionRecommendationUiState(content = loaded(ProgressionResultUi.WAIT_FOR_MORE_DATA, reasons)))

        assertShown(string(R.string.progression_result_wait_for_more_data))
        reasons.forEach(::assertShown)
        composeRule.onNodeWithText(string(R.string.progression_done)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_go_with_it)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.progression_load_failed)).assertDoesNotExist()
    }

    @Test
    fun aRecoveryAdjustmentRendersItsReasonsAndLinksToTheReadinessSheet() {
        val reasons = listOf("Heavy legs is elevated (4/5)", "Futsal session recorded in the last 24h")
        val readiness = ReadinessScore.of(heavyLegsEntry())
        setContent(
            ProgressionRecommendationUiState(
                content = loaded(ProgressionResultUi.RECOVERY_ADJUSTMENT, reasons, readiness = readiness),
            ),
        )

        assertShown(string(R.string.progression_result_recovery_adjustment))
        reasons.forEach(::assertShown)
        val band = string(readinessBandLabel(readiness.band))
        composeRule
            .onNodeWithContentDescription(string(R.string.home_readiness_content_description, readiness.score, band))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.readiness_sheet_title)).assertIsDisplayed()
    }

    @Test
    fun yourCallOffersTheThreeOutcomesWithTheOneInForceSelected() {
        val picked = mutableListOf<ProgressionResultUi>()
        setContent(
            ProgressionRecommendationUiState(
                content = loaded(ProgressionResultUi.INCREASE_LOAD, listOf("reason")),
                choosing = true,
            ),
            onPick = { picked += it },
        )

        composeRule.onNodeWithText(string(R.string.progression_choose_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_option_suggested)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_result_increase_load)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progression_result_reduce_load)).performClick()
        assertEquals(listOf(ProgressionResultUi.REDUCE_LOAD), picked)
    }

    @Test
    fun aRecordedOverrideShowsWhoDecidedWhatAndKeepsTheReasons() {
        setContent(
            ProgressionRecommendationUiState(
                content =
                    loaded(
                        ProgressionResultUi.INCREASE_LOAD,
                        listOf("Every working set reached 8+ reps"),
                        choice = RecommendationChoice(ProgressionResultUi.MAINTAIN_LOAD, Instant.parse("2026-08-11T09:00:00Z")),
                    ),
            ),
        )

        composeRule.onNodeWithText(string(R.string.progression_overridden_title)).assertIsDisplayed()
        assertShown(string(R.string.progression_record_suggested, string(R.string.progression_result_increase_load)))
        assertShown(string(R.string.progression_record_chosen, string(R.string.progression_result_maintain_load)))
        assertShown("Every working set reached 8+ reps")
        composeRule.onNodeWithText(string(R.string.progression_change_mind)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_done)).assertIsDisplayed()
    }

    /** Heavy legs 4 and leg DOMS 3: a score with drivers to name. */
    private fun heavyLegsEntry(): RecoveryEntry =
        (
            RecoveryEntry.create(
                id = RecoveryEntryId("recovery-1"),
                date = LocalDate.parse("2026-08-11"),
                sleepQuality = 4,
                energy = 3,
                legDoms = 3,
                heelStiffness = 1,
                painWhileWalking = 0,
                heavyLegs = 4,
                futsalInPrevious24h = false,
                futsalExpectedNext24h = false,
                notes = null,
                createdAt = Instant.parse("2026-08-11T07:00:00Z"),
                updatedAt = Instant.parse("2026-08-11T07:00:00Z"),
            ) as DomainResult.Success
        ).value
}
