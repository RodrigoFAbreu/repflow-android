package com.repflow.app.presentation.backup

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The real [RestoringOverlayHost] around a button that fills the content and a Back probe. Each blocking
 * check has a control with the flag down, so it can be seen to fail. Back goes through the dispatcher,
 * the path a back gesture takes, because a key event could be eaten by the key swallowing and hide a
 * dropped handler. Keys are sent at the window, as a keyboard sends them.
 */
class RestoringOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var active by mutableStateOf(true)
    private var clicks = 0
    private var backs = 0
    private val focusRequester = FocusRequester()
    private lateinit var inputModeManager: InputModeManager

    private fun string(id: Int): String = composeRule.activity.getString(id)

    private fun showHost(startActive: Boolean = true) {
        active = startActive
        composeRule.setContent {
            RepFlowTheme {
                inputModeManager = LocalInputModeManager.current
                RestoringOverlayHost(active = active) {
                    BackHandler(true) { backs++ }
                    Button(onClick = { clicks++ }, modifier = Modifier.fillMaxSize().focusRequester(focusRequester)) {
                        Text(BUTTON)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setFlag(value: Boolean) {
        composeRule.runOnIdle { active = value }
        composeRule.waitForIdle()
    }

    private fun tapTheMiddle() {
        composeRule.onRoot().performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun pressKeys(vararg keys: Int) {
        keys.forEach {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(it)
            composeRule.waitForIdle()
        }
    }

    /** A button takes focus only in keyboard mode. */
    private fun focusTheButton() {
        composeRule.runOnIdle {
            inputModeManager.requestInputMode(InputMode.Keyboard)
            focusRequester.requestFocus()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theTitleAndBodyShowOnlyWhileTheFlagIsUp() {
        showHost()
        composeRule.onNodeWithText(string(R.string.backup_restoring_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restoring_body)).assertIsDisplayed()

        setFlag(false)

        composeRule.onNodeWithText(string(R.string.backup_restoring_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.backup_restoring_body)).assertDoesNotExist()
    }

    @Test
    fun aTouchReachesNothingWhileTheFlagIsUp() {
        showHost()

        tapTheMiddle()
        assertEquals(0, clicks)

        setFlag(false)
        tapTheMiddle()
        assertEquals(1, clicks)
    }

    @Test
    fun backReachesNothingWhileTheFlagIsUp() {
        showHost()

        pressBack()
        assertEquals(0, backs)

        setFlag(false)
        pressBack()
        assertEquals(1, backs)
    }

    @Test
    fun aFlagRisingAtRuntimeTakesBackAtOnce() {
        showHost(startActive = false)

        setFlag(true)
        pressBack()

        assertEquals(0, backs)
    }

    @Test
    fun tabThenEnterPressNothingWithNoPriorFocus() {
        showHost()

        pressKeys(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER)

        assertEquals(0, clicks)
    }

    @Test
    fun keysPressNothingWithFocusSetBeforeTheFlagRose() {
        showHost(startActive = false)
        focusTheButton()
        setFlag(true)

        pressKeys(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(0, clicks)

        setFlag(false)
        pressKeys(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, clicks)
    }

    @Test
    fun theContentLeavesTheSemanticsTreeWhileTheFlagIsUp() {
        showHost()
        composeRule.onNodeWithText(BUTTON).assertDoesNotExist()

        setFlag(false)
        composeRule.onNodeWithText(BUTTON).assertExists()
    }

    /** A property, not speech: what a screen reader really says is checked in the app. */
    @Test
    fun theTitleAndBodyAreOneNodeInAPoliteLiveRegion() {
        showHost()

        composeRule
            .onNodeWithText(string(R.string.backup_restoring_title))
            .assert(hasText(string(R.string.backup_restoring_body)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private companion object {
        const val BUTTON = "Underneath"
    }
}
