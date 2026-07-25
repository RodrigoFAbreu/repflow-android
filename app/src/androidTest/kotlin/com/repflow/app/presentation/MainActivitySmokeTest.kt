package com.repflow.app.presentation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Baseline instrumentation smoke test.
 *
 * Compiled via `assembleDebugAndroidTest` as part of Milestone 0's automated
 * verification. Actual execution on a device or emulator has not yet been
 * confirmed from this session and must be run separately before it can be
 * considered validated.
 */
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityRendersRootScreen() {
        composeRule.onNodeWithText("RepFlow").assertExists()
    }
}
