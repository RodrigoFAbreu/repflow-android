package com.repflow.app.presentation.backup

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private var snapshotRestores = 0
    private var confirms = 0
    private var cancels = 0
    private var statusShowing = 0
    private var statusShown = 0

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private val copyAt = Instant.parse("2026-10-02T08:15:00Z")

    private fun actions() =
        BackupScreenActions(
            onBack = {},
            onExportBackup = { exports++ },
            onRestoreBackup = { restorePicks++ },
            onRestoreSnapshot = { snapshotRestores++ },
            onExportCsv = { csvExports++ },
            onRestoreConfirmed = { confirms++ },
            onRestoreCancelled = { cancels++ },
            onStatusShowing = { statusShowing++ },
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
    fun aRunningWorkoutIsWarnedAboutAndTheConfirmationStillWorks() {
        render(BackupUiState(isLastBackupLoaded = true, pendingRestoreJson = "{}", hasRunningWorkout = true, now = now))

        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_running_workout)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_body)).assertExists()
        assertTheConfirmationButtonsEachReportOnce()
    }

    @Test
    fun withNoRunningWorkoutTheWarningIsLeftOutAndTheConfirmationStillWorks() {
        render(BackupUiState(isLastBackupLoaded = true, pendingRestoreJson = "{}", hasRunningWorkout = false, now = now))

        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_running_workout)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_body)).assertIsDisplayed()
        assertTheConfirmationButtonsEachReportOnce()
    }

    @Test
    fun theWarningShowsWithoutScrollingAndTheBodyCanBeScrolledToAtFontScale1Point3() {
        assertTheWarningLeadsAndTheBodyScrollsAtFontScale(1.3f)
    }

    @Test
    fun theWarningShowsWithoutScrollingAndTheBodyCanBeScrolledToAtFontScale2() {
        assertTheWarningLeadsAndTheBodyScrollsAtFontScale(2f)
    }

    @Test
    fun theWarningShowsWithoutScrollingAndTheCopyParagraphsCanBeScrolledToAtFontScale2() {
        assertTheWarningLeadsAndTheBodyScrollsAtFontScale(2f, pendingRestoreSnapshotAt = copyAt, body = COPY_BODY)
    }

    @Test
    fun theWholeWarningShowsWithoutScrollingInTheRealDialogAtFontScale2() {
        renderTheRealDialogAtFontScale(2f)

        assertTheWholeWarningShowsAndTheBodyScrollsInTheRealDialog(string(R.string.backup_restore_confirm_body))
    }

    @Test
    fun theWholeWarningShowsWithoutScrollingInTheRealDialogAtFontScale2ForTheCopy() {
        renderTheRealDialogAtFontScale(2f, pendingRestoreSnapshotAt = copyAt)

        assertTheWholeWarningShowsAndTheBodyScrollsInTheRealDialog(COPY_BODY)
        composeRule.onNodeWithText(COPY_KEEPS_COPY).performScrollTo().assertIsDisplayed()
    }

    private fun assertTheWholeWarningShowsAndTheBodyScrollsInTheRealDialog(body: String) {
        val warning = composeRule.onNodeWithText(string(R.string.backup_restore_confirm_running_workout))
        val node = warning.assertIsDisplayed().fetchSemanticsNode()
        assertEquals("the font scale must reach the dialog's own window", 2f, node.layoutInfo.density.fontScale, 0.01f)
        // Clipping shortens a node's bounds but not its size, so equal heights mean no line of the warning is cut off.
        assertEquals(node.size.height.toFloat(), node.boundsInRoot.height, 1f)

        composeRule.onNodeWithText(body).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).assertIsDisplayed()
    }

    @Test
    fun cancelIsLeftOfReplaceMyDataAtFontScale1() {
        renderTheRealDialogAtFontScale(1f)

        val cancel = composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).getUnclippedBoundsInRoot()
        val replace = composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).getUnclippedBoundsInRoot()

        assertTrue("$cancel must be left of $replace", cancel.right <= replace.left)
        assertTrue(cancel.height >= MIN_BUTTON_HEIGHT)
        assertEquals(cancel.height, replace.height)
    }

    /** 1.1 is the largest scale that doesn't stack, and it wraps the longer label on this width, as a narrower phone does at 1.0. */
    @Test
    fun cancelAndReplaceMyDataKeepOneHeightWhenTheLongerLabelWraps() {
        renderTheRealDialogAtFontScale(1.1f)

        val cancel = composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).getUnclippedBoundsInRoot()
        val replace = composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).getUnclippedBoundsInRoot()

        assertTrue("$cancel must be left of $replace", cancel.right <= replace.left)
        assertEquals(cancel.height, replace.height)
    }

    @Test
    fun replaceMyDataIsAboveCancelAtFontScale2() {
        renderTheRealDialogAtFontScale(2f)

        val cancel = composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).getUnclippedBoundsInRoot()
        val replace = composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).getUnclippedBoundsInRoot()

        assertTrue("$replace must be above $cancel", replace.bottom <= cancel.top)
    }

    /** The scale goes on the context the dialog is built from, because a dialog's window takes its density from there. */
    private fun renderTheRealDialogAtFontScale(
        fontScale: Float,
        pendingRestoreSnapshotAt: Instant? = null,
    ) {
        composeRule.runOnUiThread {
            val activity = composeRule.activity
            val scaled =
                ContextThemeWrapper(activity, activity.theme).apply {
                    applyOverrideConfiguration(Configuration(activity.resources.configuration).apply { this.fontScale = fontScale })
                }
            activity.setContentView(
                ComposeView(scaled).apply {
                    setContent {
                        RepFlowTheme {
                            BackupRestoreConfirmDialog(
                                hasRunningWorkout = true,
                                preRestoreSnapshotAt = copyAt,
                                pendingRestoreSnapshotAt = pendingRestoreSnapshotAt,
                                hasTrainingData = true,
                                zone = zone,
                                onConfirm = {},
                                onCancel = {},
                            )
                        }
                    }
                },
            )
        }
    }

    /** The text alone, in a box short enough that the body has to scroll: on a tall screen the real dialog may not need to. */
    private fun assertTheWarningLeadsAndTheBodyScrollsAtFontScale(
        fontScale: Float,
        pendingRestoreSnapshotAt: Instant? = null,
        body: String = string(R.string.backup_restore_confirm_body),
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RepFlowTheme {
                    Box(Modifier.height(SHORT_BOX_HEIGHT)) {
                        BackupRestoreConfirmText(
                            hasRunningWorkout = true,
                            preRestoreSnapshotAt = null,
                            pendingRestoreSnapshotAt = pendingRestoreSnapshotAt,
                            hasTrainingData = true,
                            zone = zone,
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_running_workout)).assertIsDisplayed()
        composeRule
            .onNodeWithText(body)
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun assertTheConfirmationButtonsEachReportOnce() {
        composeRule.onNodeWithText(string(R.string.backup_restore_confirm_action)).performClick()
        assertEquals(1, confirms)
        assertEquals(0, cancels)

        composeRule.onNodeWithText(string(R.string.backup_restore_cancel_action)).performClick()
        assertEquals(1, confirms)
        assertEquals(1, cancels)
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
                            onRestoreSnapshot = {},
                            onExportCsv = {},
                            onRestoreConfirmed = {},
                            onRestoreCancelled = {},
                            onStatusShowing = {},
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

    @Test
    fun whileASnackbarIsOnScreenTheMessageIsShowingAndNotYetShown() {
        render(BackupUiState(isLastBackupLoaded = true, statusMessage = BackupStatusMessage.OperationFailed, now = now))

        composeRule.onNodeWithText(string(R.string.backup_message_operation_failed)).assertIsDisplayed()
        assertEquals(1, statusShowing)
        assertEquals(0, statusShown)

        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { statusShown == 1 }
        assertEquals(1, statusShowing)
        assertEquals(1, statusShown)
    }

    @Test
    fun theRestoreNoteNoLongerSaysItCantBeUndone() {
        render(BackupUiState(isLastBackupLoaded = true, now = now))

        composeRule.onNodeWithText(RESTORE_NOTE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withNoCopyThereIsNoCard() {
        render(BackupUiState(isLastBackupLoaded = true, now = now))

        composeRule.onNodeWithText(SAFETY_COPY_LABEL).assertDoesNotExist()
        composeRule.onNodeWithText(SAFETY_COPY_ACTION).assertDoesNotExist()
    }

    @Test
    fun withACopyTheCardShowsItsTextBelowTheRestoreCardAndReportsItsTap() {
        render(BackupUiState(isLastBackupLoaded = true, preRestoreSnapshotAt = copyAt, now = now))

        composeRule.onNodeWithText(SAFETY_COPY_LABEL).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Safety copy, 2 Oct, 08:15").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(SAFETY_COPY_META).performScrollTo().assertIsDisplayed()
        val restoreAction = composeRule.onNodeWithText(string(R.string.backup_restore_action)).getUnclippedBoundsInRoot()
        val label = composeRule.onNodeWithText(SAFETY_COPY_LABEL).getUnclippedBoundsInRoot()
        assertTrue("$label must be below $restoreAction", label.top >= restoreAction.bottom)

        val action = composeRule.onNodeWithText(SAFETY_COPY_ACTION).performScrollTo()
        assertTrue(action.getUnclippedBoundsInRoot().height >= MIN_BUTTON_HEIGHT)
        action.performClick()
        assertEquals(1, snapshotRestores)
        assertEquals(0, restorePicks)
    }

    @Test
    fun theCardWaitsWhileAnOperationIsBusy() {
        render(BackupUiState(isBusy = true, isLastBackupLoaded = true, preRestoreSnapshotAt = copyAt, now = now))

        composeRule.onNodeWithText(SAFETY_COPY_ACTION).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun theFileDialogSaysWhichCopyItReplaces() {
        render(pendingFile(hasRunningWorkout = false, preRestoreSnapshotAt = copyAt))

        composeRule.onNodeWithText(FILE_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(RUNNING_WORKOUT).assertDoesNotExist()
        assertParagraphsInOrder(FILE_BODY, FILE_KEEPS_COPY, FILE_REPLACES_COPY)
    }

    @Test
    fun theFileDialogLeadsWithTheWarningWhenAWorkoutIsRunning() {
        render(pendingFile(hasRunningWorkout = true, preRestoreSnapshotAt = copyAt))

        composeRule.onNodeWithText(FILE_TITLE).assertIsDisplayed()
        assertParagraphsInOrder(RUNNING_WORKOUT, FILE_BODY, FILE_KEEPS_COPY, FILE_REPLACES_COPY)
    }

    @Test
    fun withNoCopyTheFileDialogHasNoSafetyCopyParagraph() {
        render(pendingFile(hasRunningWorkout = false, preRestoreSnapshotAt = null))

        assertParagraphsInOrder(FILE_BODY, FILE_KEEPS_COPY)
        composeRule.onNodeWithText(REPLACES_COPY_START, substring = true).assertDoesNotExist()
    }

    @Test
    fun theCopyDialogSaysWhichCopyItRestores() {
        render(pendingCopy(hasRunningWorkout = false))

        composeRule.onNodeWithText(COPY_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(RUNNING_WORKOUT).assertDoesNotExist()
        assertParagraphsInOrder(COPY_BODY, COPY_KEEPS_COPY)
    }

    @Test
    fun theCopyDialogLeadsWithTheWarningWhenAWorkoutIsRunning() {
        render(pendingCopy(hasRunningWorkout = true))

        composeRule.onNodeWithText(COPY_TITLE).assertIsDisplayed()
        assertParagraphsInOrder(RUNNING_WORKOUT, COPY_BODY, COPY_KEEPS_COPY)
    }

    @Test
    fun withNoTrainingDataTheFileDialogShowsOnlyItsFirstParagraph() {
        render(pendingFile(hasRunningWorkout = false, preRestoreSnapshotAt = copyAt).copy(hasTrainingData = false))

        composeRule.onNodeWithText(FILE_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(FILE_KEEPS_COPY).assertDoesNotExist()
        composeRule.onNodeWithText(REPLACES_COPY_START, substring = true).assertDoesNotExist()
    }

    @Test
    fun withNoTrainingDataTheCopyDialogShowsOnlyItsFirstParagraph() {
        render(pendingCopy(hasRunningWorkout = false).copy(hasTrainingData = false))

        composeRule.onNodeWithText(COPY_BODY).assertIsDisplayed()
        composeRule.onNodeWithText(COPY_KEEPS_COPY).assertDoesNotExist()
    }

    private fun pendingFile(
        hasRunningWorkout: Boolean,
        preRestoreSnapshotAt: Instant?,
    ) = BackupUiState(
        isLastBackupLoaded = true,
        pendingRestoreJson = "{}",
        hasRunningWorkout = hasRunningWorkout,
        preRestoreSnapshotAt = preRestoreSnapshotAt,
        now = now,
    )

    private fun pendingCopy(hasRunningWorkout: Boolean) =
        pendingFile(hasRunningWorkout, preRestoreSnapshotAt = copyAt).copy(pendingRestoreSnapshotAt = copyAt)

    private fun assertParagraphsInOrder(vararg paragraphs: String) {
        val tops =
            paragraphs.map {
                composeRule
                    .onNodeWithText(it)
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                    .top
            }

        assertEquals(tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
    }

    private companion object {
        const val SNACKBAR_WAIT_MILLIS = 10_000L
        val SHORT_BOX_HEIGHT = 100.dp
        val MIN_BUTTON_HEIGHT = 48.dp

        // The drawn text from boards 40j and 40k, typed out: a string resource compared with itself proves nothing.
        const val RESTORE_NOTE =
            "Restoring replaces all data on this phone with the file's contents. Your settings stay. RepFlow keeps a copy " +
                "of your current data first, and you can restore that copy from this screen. Export a backup first."
        const val RUNNING_WORKOUT = "A workout is running on this phone. Restoring deletes it, with every set you've logged."
        const val FILE_TITLE = "Restore this backup?"
        const val FILE_BODY =
            "This replaces every workout, plan, exercise, recovery entry and recommendation on this phone with the " +
                "file's contents. Your settings stay as they are."
        const val FILE_KEEPS_COPY = "RepFlow first keeps a copy of what is on this phone. You can restore it from Backup and restore."
        const val FILE_REPLACES_COPY = "This replaces the safety copy from 2 Oct, 08:15."
        const val REPLACES_COPY_START = "This replaces the safety copy"
        const val COPY_TITLE = "Restore the safety copy?"
        const val COPY_BODY =
            "This replaces every workout, plan, exercise, recovery entry and recommendation on this phone with the " +
                "safety copy from 2 Oct, 08:15. Your settings stay as they are."
        const val COPY_KEEPS_COPY = "RepFlow first keeps a copy of what is on this phone, so you can switch back."
        const val SAFETY_COPY_LABEL = "BEFORE YOUR LAST RESTORE"
        const val SAFETY_COPY_META =
            "RepFlow kept this copy of your data just before you restored. It stays on this phone, and the next restore replaces it."
        const val SAFETY_COPY_ACTION = "Restore the safety copy"
    }
}
