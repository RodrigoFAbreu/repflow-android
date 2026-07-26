package com.repflow.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.repflow.app.R
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end instrumentation smoke test: boots [MainActivity] through Hilt
 * and asserts the exercise list - the app's start destination (D-1) - is
 * the first thing rendered. Replaces the Milestone 0 placeholder
 * `MainActivitySmokeTest` now that a real `NavHost` exists.
 *
 * The per-destination tests below exist because a registered `NavHost`
 * route is not proof a screen can actually be constructed: `hiltViewModel()`
 * silently fails at runtime (not compile time) if the target ViewModel is
 * missing `@HiltViewModel`, which is exactly the regression these guard
 * against (found during Milestone 8 planning on Recovery/History/Backup).
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

    @Test
    fun recoveryDestinationOpensWithoutCrashing() {
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_recovery_content_description),
            ).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.recovery_futsal_title))
            .assertIsDisplayed()
    }

    @Test
    fun historyDestinationOpensWithoutCrashing() {
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_history_content_description),
            ).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_title))
            .assertIsDisplayed()
    }

    @Test
    fun backupDestinationOpensWithoutCrashing() {
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_backup_content_description),
            ).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_title))
            .assertIsDisplayed()
    }
}
