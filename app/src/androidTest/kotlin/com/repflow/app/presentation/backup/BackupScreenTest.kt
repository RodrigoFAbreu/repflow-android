package com.repflow.app.presentation.backup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * The dedicated Backup screen (`5d` / turn 8's `8c`, remediation-1-remediation-1
 * CP8): the hero reads `No backup yet` before the first backup and `Last backup
 * <when>` over its date after; `Export backup now`, the CSV row and `Restore
 * from a backup` each report their own tap; and a pending restore is never
 * applied without the existing destructive confirmation.
 */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var exports = 0
    private var csvExports = 0
    private var restorePicks = 0
    private var confirms = 0
    private var cancels = 0
    private var statusShown = 0

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-03T12:00:00Z")

    private fun actions() =
        BackupScreenActions(
            onBack = {},
            onExportBackup = { exports++ },
            onRestoreBackup = { restorePicks++ },
            onExportCsv = { csvExports++ },
            onRestoreConfirmed = { confirms++ },
            onRestoreCancelled = { cancels++ },
            onStatusShown = { statusShown++ },
        )

    private fun render(state: BackupUiState) {
        composeRule.setContent {
            RepFlowTheme { BackupScreen(uiState = state, actions = actions(), zone = zone) }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    @Test
    fun beforeTheFirstBackupTheHeroSaysSoAndOffersTheFirstExport() {
        render(BackupUiState(isLastBackupLoaded = true, now = now))

        composeRule.onNodeWithText(string(R.string.backup_none_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_none_meta)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_export_first_action)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_export_note)).assertIsDisplayed()
    }

    @Test
    fun afterABackupTheHeroShowsHowLongAgoAndWhen() {
        render(BackupUiState(isLastBackupLoaded = true, lastBackupAt = Instant.parse("2026-10-01T21:04:00Z"), now = now))

        composeRule.onNodeWithText(string(R.string.backup_last_days_ago, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_last_date_time, "1 Oct", "21:04")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_export_action)).assertIsDisplayed()
    }

    @Test
    fun aBackupFromTodayReadsAsToday() {
        render(BackupUiState(isLastBackupLoaded = true, lastBackupAt = Instant.parse("2026-10-03T09:00:00Z"), now = now))
        composeRule.onNodeWithText(string(R.string.backup_last_today)).assertIsDisplayed()
    }

    @Test
    fun exportCsvAndRestoreEachReportTheirOwnTap() {
        render(BackupUiState(isLastBackupLoaded = true, now = now))

        composeRule.onNodeWithText(string(R.string.backup_export_first_action)).performClick()
        composeRule.onNodeWithText(string(R.string.backup_csv_export_action)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).performScrollTo().performClick()

        assertEquals(listOf(1, 1, 1), listOf(exports, csvExports, restorePicks))
        assertEquals(0, confirms)
    }

    @Test
    fun theActionsWaitWhileAnOperationIsBusy() {
        render(BackupUiState(isBusy = true, isLastBackupLoaded = true, now = now))

        composeRule.onNodeWithText(string(R.string.backup_export_first_action)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.backup_csv_export_action)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aPendingRestoreAsksForTheDestructiveConfirmationBeforeAnythingIsReplaced() {
        render(BackupUiState(isLastBackupLoaded = true, pendingRestoreJson = "{}", now = now))

        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_title)).assertIsDisplayed()
        assertEquals(0, confirms)

        composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).performClick()
        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test
    fun confirmingTheRestoreReportsIt() {
        render(BackupUiState(isLastBackupLoaded = true, pendingRestoreJson = "{}", now = now))

        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).performClick()

        assertEquals(1, confirms)
    }

    @Test
    fun anExportSuccessShowsSavedAndOtherOutcomesAreSnackbars() {
        composeRule.setContent {
            var state by remember {
                mutableStateOf(BackupUiState(isLastBackupLoaded = true, statusMessage = BackupStatusMessage.ExportSucceeded, now = now))
            }
            RepFlowTheme {
                BackupScreen(
                    uiState = state,
                    actions =
                        BackupScreenActions(
                            onBack = {},
                            onExportBackup = {},
                            onRestoreBackup = {},
                            onExportCsv = {},
                            onRestoreConfirmed = {},
                            onRestoreCancelled = {},
                            onStatusShown = {
                                statusShown++
                                state =
                                    state.copy(
                                        statusMessage =
                                            if (statusShown == 1) BackupStatusMessage.OperationFailed else null,
                                    )
                            },
                        ),
                    zone = zone,
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.backup_export_saved)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_message_operation_failed)).assertIsDisplayed()
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { statusShown == 2 }
    }

    private companion object {
        const val SNACKBAR_WAIT_MILLIS = 10_000L
    }
}
