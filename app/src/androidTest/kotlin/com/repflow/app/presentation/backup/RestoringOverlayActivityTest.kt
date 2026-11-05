package com.repflow.app.presentation.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.repflow.app.R
import com.repflow.app.presentation.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The Restoring screen on the real [MainActivity], walked from Home to Backup. The screen is told by
 * `backup_restore_action`, never by its title: `backup_title` and `settings_backup_row` are the same
 * words, so a title check passes on Settings too.
 *
 * The recreations are the point. After one the overlay is in the first composition while the Scaffold
 * in `RepFlowNavHost` subcomposes `NavHost` during measure, so `NavHost`'s back handler registers after
 * the overlay's, and Back must still not leave the Backup screen, from that moment on.
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

    /**
     * With the test clock stopped, so the first layout has run and `NavHost`'s back handler is registered,
     * and no frame has come since. Anything in the app that waits for a frame hasn't happened yet.
     */
    private fun recreateAndStopBeforeTheNextFrame() {
        composeRule.mainClock.autoAdvance = false
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        assertTheOverlayCoversTheApp()
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

    /** The likeliest real path: Home during a restore, then back to the app, once and then again. */
    @Test
    fun backStaysOnBackupAfterReturnsFromTheBackgroundWithNoRecreation() {
        openBackup()
        beginRestore()

        repeat(2) {
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            composeRule.waitForIdle()
            assertTheOverlayCoversTheApp()

            pressBack()
        }

        endRestore()
        assertStillOnBackup()
    }

    @Test
    fun backStaysOnBackupBeforeTheNextFrameAfterARecreationAndLeavesOnceTheRestoreEnds() {
        openBackup()
        beginRestore()

        recreateAndStopBeforeTheNextFrame()
        pressBack()

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTheOverlayCoversTheApp()
        endRestore()
        assertStillOnBackup()

        pressBack()
        composeRule.onNodeWithText(string(R.string.settings_backup_row)).assertExists()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertDoesNotExist()
    }

    /**
     * A real restore through the real screen, so its outcome takes the real way there. A file that isn't a
     * backup is rejected after the confirmation and changes nothing, so no other test sees this one. The
     * test holds the flag up itself, because a restore this small would end before the recreation.
     */
    @Test
    fun aRestoresOutcomeStillShowsOnBackupAfterABackBeforeTheNextFrame() {
        val notABackup = File(composeRule.activity.cacheDir, "not-a-backup.json").apply { writeText("not a backup") }
        Intents.init()
        try {
            Intents
                .intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(notABackup))))
            openBackup()
            val restoreAction = string(R.string.backup_restore_action)
            composeRule.waitUntil(WAIT_MILLIS) {
                composeRule.onAllNodes(hasText(restoreAction).and(isEnabled())).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(restoreAction).performScrollTo().performClick()
            composeRule.waitForIdle()

            beginRestore()
            composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).performClick()
            recreateAndStopBeforeTheNextFrame()
            pressBack()

            composeRule.mainClock.autoAdvance = true
            endRestore()
            assertStillOnBackup()
            val outcome = string(R.string.backup_message_invalid_backup)
            composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodes(hasText(outcome)).fetchSemanticsNodes().isNotEmpty() }
            composeRule.onNodeWithText(outcome).assertIsDisplayed()
        } finally {
            Intents.release()
            notABackup.delete()
        }
    }

    /** With no restore, Back does leave, so the checks above can tell the two screens apart. */
    @Test
    fun withNoRestoreBackLeavesBackupForSettings() {
        openBackup()

        pressBack()

        composeRule.onNodeWithText(string(R.string.settings_backup_row)).assertExists()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertDoesNotExist()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
