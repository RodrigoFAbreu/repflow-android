package com.repflow.app.presentation.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import com.repflow.app.R
import com.repflow.app.presentation.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Real end-to-end coverage (Milestone 8, implementation-review finding #4)
 * for an unreadable restore file: [Intents.intending] stubs the
 * `OpenDocument` picker to return a `content://` `Uri` with no registered
 * provider, so [android.content.ContentResolver.openInputStream] throws
 * exactly as it would for a revoked-permission or otherwise unreadable real
 * file. Confirms this reaches [BackupViewModel.onRestoreFileReadFailed] -
 * an `OperationFailed` message, no crash, and no restore-confirmation
 * dialog (nothing was ever staged) - rather than propagating the exception
 * out of the `ActivityResultCallback` and crashing the screen.
 */
class BackupRouteUnreadableRestoreFileTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun initIntents() {
        Intents.init()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    @Test
    fun anUnreadableRestoreFileReportsFailureWithoutCrashingOrStagingARestore() {
        val unreadableUri = Uri.parse("content://com.repflow.app.nonexistent.provider/fake")
        Intents
            .intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(unreadableUri)))

        // Backup is not a tab: Home's Settings affordance, Settings' `Backup and
        // restore` row (remediation-1-remediation-1 CP8), then the screen's own
        // button.
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.home_settings_content_description),
            ).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.settings_backup_row))
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_restore_action))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_message_operation_failed))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_restore_confirm_title))
            .assertDoesNotExist()
    }
}
