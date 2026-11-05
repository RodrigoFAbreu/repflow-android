package com.repflow.app.presentation.backup

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import com.repflow.app.R
import com.repflow.app.presentation.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test

/**
 * The Restoring screen on the real [MainActivity], walked from Home to Backup. The screen is told by
 * `backup_restore_action`, never by its title: `backup_title` and `settings_backup_row` are the same
 * words, so a title check passes on Settings too.
 *
 * The recreations are the point. After one the overlay is in the first composition while the Scaffold
 * in `RepFlowNavHost` subcomposes `NavHost` during measure, so `NavHost`'s back handler registers after
 * the overlay's, and without the overlay's second registration Back would leave the Backup screen.
 */
class RestoringOverlayActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var openRestores = 0

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun openBackup() {
        composeRule.onNodeWithContentDescription(string(R.string.home_settings_content_description)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_backup_row)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertExists()
    }

    private fun beginRestore() {
        composeRule.activity.restoreInProgress.begin()
        openRestores++
        composeRule.waitForIdle()
    }

    private fun endRestore() {
        composeRule.activity.restoreInProgress.end()
        openRestores--
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun assertTheOverlayCoversTheApp() {
        composeRule.onNodeWithText(string(R.string.backup_restoring_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertDoesNotExist()
    }

    /** The overlay lifted and the user is still on Backup. */
    private fun assertStillOnBackup() {
        composeRule.onNodeWithText(string(R.string.backup_restoring_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertExists()
    }

    /** The singleton outlives the test, and a flag left up would cover the next one. */
    @After
    fun endAnyRestoreLeftOpen() {
        repeat(openRestores) { composeRule.activity.restoreInProgress.end() }
    }

    @Test
    fun backStaysOnBackupWhileTheOverlayIsUp() {
        openBackup()
        beginRestore()
        assertTheOverlayCoversTheApp()

        pressBack()

        endRestore()
        assertStillOnBackup()
    }

    @Test
    fun backStaysOnBackupAfterARecreation() {
        openBackup()
        beginRestore()

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertTheOverlayCoversTheApp()

        pressBack()

        endRestore()
        assertStillOnBackup()
    }

    @Test
    fun backStaysOnBackupAfterARecreationThenBackgroundAndReturn() {
        openBackup()
        beginRestore()

        composeRule.activityRule.scenario.recreate()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        assertTheOverlayCoversTheApp()

        pressBack()

        endRestore()
        assertStillOnBackup()
    }

    /** With no restore, Back does leave, so the checks above can tell the two screens apart. */
    @Test
    fun withNoRestoreBackLeavesBackupForSettings() {
        openBackup()

        pressBack()

        composeRule.onNodeWithText(string(R.string.settings_backup_row)).assertExists()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertDoesNotExist()
    }
}
