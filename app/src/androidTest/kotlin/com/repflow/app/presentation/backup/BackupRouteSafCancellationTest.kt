package com.repflow.app.presentation.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * [BackupRoute] launches to return [Activity.RESULT_CANCELED], exactly what
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

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_backup_content_description),
            ).performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_export_action))
            .performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_message_operation_failed))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.backup_export_action))
            .assertIsEnabled()
    }
}
