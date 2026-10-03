package com.repflow.app.presentation.recovery

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Stateless Compose coverage for [RecoveryFutsalScreen] on `3c`'s composition
 * (remediation-1 CP13 item 6 - the checkpoint's grep found no existing test,
 * so these are new): the scale rows' end labels and polarity (items 1 and
 * 5), the futsal toggles, steppers and load (item 2), and the one `Save
 * entry`.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryFutsalScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 8, 11)

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun setScreen(
        uiState: RecoveryFutsalUiState,
        onBack: () -> Unit = {},
        onHistoryClick: () -> Unit = {},
        onScaleFieldChanged: (RecoveryScaleField, Int) -> Unit = { _, _ -> },
        onFutsalPreviousToggled: (Boolean) -> Unit = {},
        onDurationChanged: (String) -> Unit = {},
        onSaveEntry: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                RecoveryFutsalScreen(
                    uiState = uiState,
                    onBack = onBack,
                    onHistoryClick = onHistoryClick,
                    onDateChanged = {},
                    onScaleFieldChanged = onScaleFieldChanged,
                    onFutsalPreviousToggled = onFutsalPreviousToggled,
                    onFutsalNextToggled = {},
                    onNotesChanged = {},
                    onDurationChanged = onDurationChanged,
                    onSessionRpeChanged = {},
                    onSaveEntry = onSaveEntry,
                    onMessageShown = {},
                )
            }
        }
    }

    private fun loaded(): RecoveryFutsalUiState = RecoveryFutsalUiState(isLoading = false, date = today, today = today)

    @Test
    fun everyScaleRowIsLabelledAtBothEndsWithItsPolarity() {
        setScreen(loaded())

        fun ends(
            low: Int,
            high: Int,
        ) = string(R.string.repflow_scale_end_labels, string(low), string(high))
        composeRule
            .onAllNodesWithText(ends(R.string.recovery_scale_sleep_low, R.string.recovery_scale_sleep_high))
            .assertCountEquals(1)
        composeRule
            .onAllNodesWithText(ends(R.string.recovery_scale_energy_low, R.string.recovery_scale_energy_high))
            .assertCountEquals(1)
        // Leg DOMS, heel stiffness, pain while walking, heavy legs: a high number is bad.
        composeRule
            .onAllNodesWithText(ends(R.string.recovery_scale_severity_low, R.string.recovery_scale_severity_high))
            .assertCountEquals(4)
    }

    @Test
    fun tappingAScaleCellReportsItsFieldAndValue() {
        var changed: Pair<RecoveryScaleField, Int>? = null
        setScreen(loaded(), onScaleFieldChanged = { field, value -> changed = field to value })

        // Six rows each offer a `4`; the first is sleep quality's.
        composeRule.onAllNodesWithText("4")[0].performClick()

        assertEquals(RecoveryScaleField.SLEEP_QUALITY to 4, changed)
    }

    @Test
    fun theDateRowReadsTodayOnTodaysEntry() {
        setScreen(loaded())

        composeRule.onNodeWithText(string(R.string.recovery_futsal_date_today, "11 Aug 2026")).assertIsDisplayed()
    }

    @Test
    fun theFutsalBlockShowsOnlyWhilePlayedInLast24hIsOn() {
        var toggled: Boolean? = null
        setScreen(loaded(), onFutsalPreviousToggled = { toggled = it })

        composeRule.onNodeWithText(string(R.string.recovery_futsal_duration_minutes)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.recovery_futsal_futsal_previous_24h)).performScrollTo().performClick()

        assertEquals(true, toggled)
    }

    @Test
    fun theFutsalBlockShowsTheStepperValuesAndTheTrainingLoad() {
        setScreen(loaded().copy(futsalInPrevious24h = true, durationMinutesInput = "60", sessionRpeInput = "7.0"))

        composeRule.onNodeWithText(string(R.string.recovery_futsal_duration_minutes)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("60").assertExists()
        composeRule.onNodeWithText("7").assertExists()
        composeRule.onNodeWithText(string(R.string.recovery_futsal_load, "420")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun steppingAnEmptyMinutesFieldReportsTheFirstStep() {
        var duration: String? = null
        setScreen(loaded().copy(futsalInPrevious24h = true), onDurationChanged = { duration = it })

        composeRule
            .onNodeWithContentDescription(string(R.string.recovery_futsal_duration_increase))
            .performScrollTo()
            .performClick()

        assertEquals("5", duration)
    }

    private fun loadedWithAllScales(): RecoveryFutsalUiState =
        loaded().copy(sleepQuality = 3, energy = 3, legDoms = 1, heelStiffness = 1, painWhileWalking = 0, heavyLegs = 2)

    /** Functional review A6: a day with nothing chosen cannot be saved, and the screen says why. */
    @Test
    fun saveEntryIsDisabledUntilEveryScaleHasAValue() {
        var saves = 0
        setScreen(loaded(), onSaveEntry = { saves++ })

        composeRule.onNodeWithText(string(R.string.recovery_futsal_scales_incomplete)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.recovery_futsal_save_entry)).assertIsNotEnabled().performClick()
        assertEquals(0, saves)
    }

    /** Functional review R2-F-3: the reason sits in the pinned bar, directly above Save, never under it. */
    @Test
    fun theIncompleteScalesHintSitsDirectlyAboveTheSaveButton() {
        setScreen(loaded())

        val hint = composeRule.onNodeWithText(string(R.string.recovery_futsal_scales_incomplete)).getBoundsInRoot()
        val save = composeRule.onNodeWithText(string(R.string.recovery_futsal_save_entry)).getBoundsInRoot()

        assertTrue("hint ${hint.bottom} must end above Save ${save.top}", hint.bottom <= save.top)
        assertTrue("hint ${hint.bottom} must be adjacent to Save ${save.top}", save.top - hint.bottom < 40.dp)
    }

    @Test
    fun saveEntryIsThePinnedAction() {
        var saves = 0
        setScreen(loadedWithAllScales(), onSaveEntry = { saves++ })

        composeRule.onNodeWithText(string(R.string.recovery_futsal_save_entry)).assertIsDisplayed().performClick()
        assertEquals(1, saves)
    }

    /** O8: while a newly picked date's values load, `Save entry` cannot store the previous date's fields onto it. */
    @Test
    fun saveEntryIsDisabledWhileADateLoads() {
        var saves = 0
        setScreen(loaded().copy(isLoading = true), onSaveEntry = { saves++ })

        composeRule.onNodeWithText(string(R.string.recovery_futsal_save_entry)).assertIsNotEnabled().performClick()
        assertEquals(0, saves)
    }

    @Test
    fun aSavedEntryReadsSaved() {
        setScreen(loaded().copy(isEntrySaved = true))

        composeRule.onNodeWithText(string(R.string.recovery_futsal_saved)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.recovery_futsal_save_entry)).assertDoesNotExist()
    }

    @Test
    fun backAndHistoryAreReachable() {
        var back = false
        var history = false
        setScreen(loaded(), onBack = { back = true }, onHistoryClick = { history = true })

        composeRule.onNodeWithContentDescription(string(R.string.repflow_back_content_description)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.recovery_futsal_view_history_content_description)).performClick()

        assertTrue(back)
        assertTrue(history)
    }
}
