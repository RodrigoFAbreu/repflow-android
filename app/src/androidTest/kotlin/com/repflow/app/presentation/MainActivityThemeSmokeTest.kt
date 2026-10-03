package com.repflow.app.presentation

import android.view.View
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.WindowCompat
import com.repflow.app.R
import com.repflow.app.application.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * remediation-1-remediation-1 CP6: [MainActivity] boots in each stored
 * `Theme` and the system bars follow the applied scheme, not the device's - a
 * forced `Dark` or `Light` keeps legible status-bar icons whatever the system
 * is. The mode is written through the activity's own injected repository and
 * put back to `System` afterwards, so the device settings are left as found.
 */
class MainActivityThemeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun restoreTheNeutralTheme() {
        runBlocking { composeRule.activity.settingsRepository.update { it.copy(theme = ThemeMode.SYSTEM) } }
    }

    private fun lightStatusBarIcons(): Boolean {
        val window = composeRule.activity.window
        val view: View = window.decorView
        return WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars
    }

    private fun applyTheme(mode: ThemeMode) {
        runBlocking { composeRule.activity.settingsRepository.update { it.copy(theme = mode) } }
        composeRule.waitForIdle()
    }

    @Test
    fun darkBootsHomeAndDrawsLightStatusBarIcons() {
        applyTheme(ThemeMode.DARK)
        composeRule.waitUntil(WAIT_MILLIS) { !lightStatusBarIcons() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_greeting)).assertIsDisplayed()
        assertEquals(false, lightStatusBarIcons())
    }

    @Test
    fun lightBootsHomeAndDrawsDarkStatusBarIcons() {
        applyTheme(ThemeMode.LIGHT)
        composeRule.waitUntil(WAIT_MILLIS) { lightStatusBarIcons() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_greeting)).assertIsDisplayed()
        assertEquals(true, lightStatusBarIcons())
    }

    @Test
    fun systemBootsHome() {
        applyTheme(ThemeMode.SYSTEM)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_greeting)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
