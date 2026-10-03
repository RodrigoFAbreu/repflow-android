package com.repflow.app.presentation.designsystem.components

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

/**
 * Behaviour of remediation-1 CP3's interactive primitives that a JVM test
 * cannot reach: the stepper's value opening the keypad (`6b`), the keypad's
 * Set and Cancel, the scale row's end labels, and the list row's tap target.
 */
@RunWith(AndroidJUnit4::class)
class RepFlowStructuralPrimitivesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun setStepper(initial: BigDecimal?): () -> BigDecimal? {
        var value by mutableStateOf(initial)
        composeRule.setContent {
            RepFlowTheme {
                RepFlowStepper(
                    value = value,
                    onValueChange = { value = it },
                    step = BigDecimal("2.5"),
                    keypadTitle = "Weight (kg)",
                    decrementContentDescription = "Less weight",
                    incrementContentDescription = "More weight",
                )
            }
        }
        return { value }
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
