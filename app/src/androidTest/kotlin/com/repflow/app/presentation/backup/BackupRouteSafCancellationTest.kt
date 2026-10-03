package com.repflow.app.presentation.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
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
 * Real end-to-end coverage (Milestone 8, CP14) for the "user cancels the SAF
 * export picker" path: [Intents.intending] stubs the `CreateDocument` intent
 * the Backup screen ([rememberBackupFileActions]) launches to return [Activity.RESULT_CANCELED], exactly what
 * Android delivers when a real user backs out of the system file picker
 * without choosing a destination. Confirms this reaches
 * [BackupViewModel.onExportWriteCancelled] - busy state cleared, no failure
 * message - rather than either silently hanging or, as the pre-CP14
 * behavior did, reporting success before the SAF write (or cancellation)
 * had even happened.
 */
class BackupRouteSafCancellationTest {
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
    fun cancellingTheExportPickerClearsBusyWithoutShowingAFailureMessage() {
        Intents
            .intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

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
        exportButton().performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_message_operation_failed))
            .assertDoesNotExist()
        exportButton().assertIsEnabled()
    }

    /** The hero's export button: `Export your first backup` on a fresh install, `Export backup now` once one exists. */
    private fun exportButton() =
        composeRule.onNode(
            hasText(composeRule.activity.getString(R.string.backup_export_first_action))
                .or(hasText(composeRule.activity.getString(R.string.backup_export_action))),
        )
}
