package com.repflow.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.repflow.app.R
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end instrumentation smoke test: boots [MainActivity] through Hilt
 * and asserts the exercise list - the app's start destination (D-1) - is
 * the first thing rendered. Replaces the Milestone 0 placeholder
 * `MainActivitySmokeTest` now that a real `NavHost` exists.
 */
class MainActivityNavHostSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivityRendersTheExerciseListAsTheStartDestination() {
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_title))
            .assertIsDisplayed()
    }
}
