package com.repflow.app.presentation.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Behaviour of remediation-1 CP3's interactive primitives that a JVM test
 * cannot reach: the stepper's value opening the keypad (`6b`), the keypad's
 * Set and Cancel, the scale row's end labels, and the list row's tap target.
 * The keypad opening on its value, selected, is `50h`; its m:ss form is `10l`.
 */
@RunWith(AndroidJUnit4::class)
class RepFlowStructuralPrimitivesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    /** Every value the stepper reported, to tell `Set` from a dismissal when the value is the same. */
    private val changes = mutableListOf<BigDecimal>()

    @Composable
    private fun Stepper(
        value: BigDecimal?,
        onValueChange: (BigDecimal) -> Unit,
        allowDecimal: Boolean,
        durationEntry: Boolean,
    ) {
        RepFlowTheme {
            RepFlowStepper(
                value = value,
                onValueChange = {
                    changes += it
                    onValueChange(it)
                },
                step = BigDecimal("2.5"),
                keypadTitle = "Weight (kg)",
                decrementContentDescription = "Less weight",
                incrementContentDescription = "More weight",
                allowDecimal = allowDecimal,
                durationEntry = durationEntry,
                format = { if (durationEntry) formatDuration(it.toLong()) else RepFlowStepperMath.format(it) },
            )
        }
    }

    private fun setStepper(
        initial: BigDecimal?,
        allowDecimal: Boolean = true,
        durationEntry: Boolean = false,
    ): () -> BigDecimal? {
        var value by mutableStateOf(initial)
        composeRule.setContent { Stepper(value, { value = it }, allowDecimal, durationEntry) }
        return { value }
    }

    private fun setRestorableStepper(initial: BigDecimal): StateRestorationTester {
        val tester = StateRestorationTester(composeRule)
        tester.setContent { Stepper(initial, {}, allowDecimal = true, durationEntry = false) }
        return tester
    }

    private fun selectedValue(text: String) = composeRule.onNode(hasText(text) and isSelected())

    private fun press(id: Int) = composeRule.onNodeWithText(string(id)).performClick()

    private fun pressSet() {
        press(R.string.repflow_keypad_confirm)
        composeRule.waitForIdle()
    }

    @Test
    fun theStepButtonsMoveTheValueByTheStepSize() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithContentDescription("More weight").performClick()
        composeRule.onNodeWithText("82.5").assertIsDisplayed()
        assertEquals(0, BigDecimal("82.5").compareTo(value()))
    }

    @Test
    fun tappingTheValueOpensTheKeypadAndSetConfirmsTheTypedNumber() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText("Weight (kg)").assertIsDisplayed()
        listOf("8", "2", ".", "5").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onNodeWithText(string(R.string.repflow_keypad_confirm)).performClick()
        composeRule.waitForIdle()
        assertEquals(0, BigDecimal("82.5").compareTo(value()))
    }

    @Test
    fun cancelLeavesTheValueUnchanged() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText("9").performClick()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_cancel)).performClick()
        composeRule.waitForIdle()
        assertEquals(0, BigDecimal("80").compareTo(value()))
    }

    @Test
    fun theKeypadOpensShowingTheValueSelected() {
        setStepper(BigDecimal("82.50"))
        composeRule.onNodeWithText("82.5").performClick()
        selectedValue("82.5").assertIsSelected()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_selected_caption)).assertIsDisplayed()
    }

    @Test
    fun anEmptyStepperOpensAnEmptyKeypadThatIsNotSelected() {
        setStepper(null)
        composeRule.onNodeWithText("0").performClick()
        selectedValue("0").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_selected_caption)).assertDoesNotExist()
    }

    @Test
    fun aDigitReplacesTheSelectedValueAndEndsTheSelection() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText("7").performClick()
        selectedValue("7").assertDoesNotExist()
        selectedValue("80").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_selected_caption)).assertDoesNotExist()
        pressSet()
        assertEquals(0, BigDecimal("7").compareTo(value()))
    }

    @Test
    fun setUnchangedConfirmsTheSameValue() {
        val value = setStepper(BigDecimal("82.5"))
        composeRule.onNodeWithText("82.5").performClick()
        pressSet()
        assertEquals(1, changes.size)
        assertEquals(0, BigDecimal("82.5").compareTo(changes.single()))
        assertEquals(0, BigDecimal("82.5").compareTo(value()))
    }

    @Test
    fun cancelWithTheValueStillSelectedLeavesItAndReportsNothing() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        press(R.string.repflow_keypad_cancel)
        composeRule.waitForIdle()
        assertTrue(changes.isEmpty())
        assertEquals(0, BigDecimal("80").compareTo(value()))
    }

    @Test
    fun backspaceThenSetLeavesTheValueAndClosesThePad() {
        val value = setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithContentDescription(string(R.string.repflow_keypad_clear_content_description)).performClick()
        pressSet()
        assertTrue(changes.isEmpty())
        assertEquals(0, BigDecimal("80").compareTo(value()))
        composeRule.onNodeWithText("Weight (kg)").assertDoesNotExist()
    }

    @Test
    fun backspaceReadsClearWhileSelectedAndDeleteOnceSomethingIsTyped() {
        setStepper(BigDecimal("80"))
        val clear = string(R.string.repflow_keypad_clear_content_description)
        val delete = string(R.string.repflow_keypad_backspace_content_description)
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithContentDescription(clear).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(delete).assertDoesNotExist()
        composeRule.onNodeWithText("7").performClick()
        composeRule.onNodeWithContentDescription(delete).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(clear).assertDoesNotExist()
    }

    @Test
    fun aStillSelectedValueSurvivesARestore() {
        val tester = setRestorableStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        tester.emulateSavedInstanceStateRestore()
        selectedValue("80").assertIsSelected()
    }

    @Test
    fun aHalfTypedEntrySurvivesARestoreAndIsNotSelected() {
        val tester = setRestorableStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        listOf("4", "5").forEach { composeRule.onNodeWithText(it).performClick() }
        tester.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("45").assertIsDisplayed()
        selectedValue("45").assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(string(R.string.repflow_keypad_backspace_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun aWholeNumberPadGreysItsDecimalKeyAndSaysSo() {
        setStepper(BigDecimal("80"), allowDecimal = false)
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText(RepFlowKeypadInput.DECIMAL).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_whole_numbers_caption)).assertIsDisplayed()
        composeRule.onNodeWithText("7").performClick()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_whole_numbers_caption)).assertIsDisplayed()
    }

    @Test
    fun aDecimalPadKeepsItsDecimalKeyEnabled() {
        setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText(RepFlowKeypadInput.DECIMAL).assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_whole_numbers_caption)).assertDoesNotExist()
    }

    @Test
    fun theDurationPadOpensOnTheClockAndConfirmsWholeSeconds() {
        val value = setStepper(BigDecimal(90), allowDecimal = false, durationEntry = true)
        composeRule.onNodeWithText("1:30").performClick()
        selectedValue("1:30").assertIsSelected()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_duration_caption)).assertIsDisplayed()
        composeRule.onNodeWithText(RepFlowKeypadInput.DOUBLE_ZERO).assertIsEnabled()
        composeRule.onNodeWithText(RepFlowKeypadInput.DECIMAL).assertDoesNotExist()
        listOf("2", "1", "5").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onNodeWithText("2:15").assertIsDisplayed()
        pressSet()
        assertEquals(0, BigDecimal(135).compareTo(value()))
    }

    @Test
    fun theDurationPadSetUnchangedConfirmsTheSameSeconds() {
        val value = setStepper(BigDecimal(90), allowDecimal = false, durationEntry = true)
        composeRule.onNodeWithText("1:30").performClick()
        pressSet()
        assertEquals(1, changes.size)
        assertEquals(0, BigDecimal(90).compareTo(value()))
    }

    @Test
    fun aSpokenDurationSaysMinutesAndSecondsInWords() {
        val spoken = mutableMapOf<Long, String>()
        composeRule.setContent {
            RepFlowTheme {
                listOf(0L, 1L, 45L, 60L, 61L, 90L, 120L, 900L, 7200L).forEach { spoken[it] = spokenDuration(it) }
            }
        }
        composeRule.waitForIdle()
        assertEquals(
            mapOf(
                0L to "0 seconds",
                1L to "1 second",
                45L to "45 seconds",
                60L to "1 minute",
                61L to "1 minute 1 second",
                90L to "1 minute 30 seconds",
                120L to "2 minutes",
                900L to "15 minutes",
                7200L to "120 minutes",
            ),
            spoken,
        )
    }

    @Test
    fun aScreenReaderIsGivenTheDurationStepperAndPadInWordsAsTheyChange() {
        setStepper(BigDecimal(90), allowDecimal = false, durationEntry = true)
        composeRule.onNode(hasText("1:30") and hasClickAction() and hasContentDescription("1 minute 30 seconds")).assertIsDisplayed()
        composeRule.onNodeWithText("1:30").performClick()
        composeRule.onNode(hasText("1:30") and isSelected() and hasContentDescription("1 minute 30 seconds")).assertIsDisplayed()
        listOf("1", "5", "0", "0").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onNode(hasText("15:00") and hasContentDescription("15 minutes")).assertIsDisplayed()
        pressSet()
        composeRule.onNode(hasText("15:00") and hasClickAction() and hasContentDescription("15 minutes")).assertIsDisplayed()
    }

    @Test
    fun theDurationPadSaysWhatSetWouldStore() {
        setStepper(null, allowDecimal = false, durationEntry = true)
        composeRule.onNode(hasText("0") and hasClickAction()).performClick()
        composeRule.onNode(hasText("0:00") and hasContentDescription("0 seconds")).assertIsDisplayed()
        listOf("9", "5").forEach { composeRule.onNodeWithText(it).performClick() }
        composeRule.onNode(hasText("0:95") and hasContentDescription("1 minute 35 seconds")).assertIsDisplayed()
    }

    @Test
    fun aPlainStepperAndPadKeepTheirOwnText() {
        val noDescription = SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
        setStepper(BigDecimal("82.5"))
        composeRule.onNodeWithText("82.5").assert(noDescription)
        composeRule.onNodeWithText("82.5").performClick()
        selectedValue("82.5").assert(noDescription)
    }

    @Test
    fun setCanBeScrolledTo() {
        setStepper(BigDecimal("80"))
        composeRule.onNodeWithText("80").performClick()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_confirm)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theScaleRowShowsBothEndLabels() {
        composeRule.setContent {
            RepFlowTheme {
                RepFlowScaleRow(
                    options = (0..5).map(Int::toString),
                    selectedIndex = null,
                    onSelect = {},
                    lowLabel = "Terrible",
                    highLabel = "Great",
                    label = "Sleep quality",
                )
            }
        }
        composeRule.onNodeWithText("Sleep quality").assertIsDisplayed()
        composeRule.onNodeWithText("Terrible → Great").assertIsDisplayed()
    }

    @Test
    fun aClickableListRowIsOneTapTarget() {
        var clicks = 0
        composeRule.setContent {
            RepFlowTheme {
                RepFlowListRow(title = "Backup and restore", meta = "Export, restore", onClick = { clicks++ })
            }
        }
        composeRule.onNodeWithText("Export, restore").performClick()
        assertEquals(1, clicks)
    }
}
