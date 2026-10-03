package com.repflow.app.presentation.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneOffset

/**
 * Settings, converted to `5c` (remediation-1-remediation-1 CP7), grouped as the
 * design groups it: each switch renders the stored value and reports its own
 * toggle; `Theme`, `Default rest` and `Extra set fields` render the stored
 * value and report their own choice (the two rows through their sheets); all
 * of it waits disabled until the settings have loaded; the Library and
 * Archived rows lead where they say; and `Erase all data` sits behind a typed
 * confirmation. The Data group's `Backup and restore` row leads to CP8's
 * Backup screen, which carries the backup actions.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val toggles = mutableListOf<Pair<SettingToggle, Boolean>>()
    private val themes = mutableListOf<ThemeMode>()
    private val rests = mutableListOf<Int>()
    private val extraFields = mutableListOf<ExtraSetFields>()
    private var libraryClicks = 0
    private var archivedClicks = 0
    private var backupClicks = 0
    private var eraseConfirms = 0

    private fun actions(onSettingsMessageShown: () -> Unit = {}) =
        SettingsActions(
            onBack = {},
            onLibraryClick = { libraryClicks++ },
            onArchivedClick = { archivedClicks++ },
            onBackupClick = { backupClicks++ },
            onToggle = { toggle, on -> toggles += toggle to on },
            onThemeSelected = { themes += it },
            onDefaultRestSelected = { rests += it },
            onExtraSetFieldsSelected = { extraFields += it },
            onEraseAllDataConfirmed = { eraseConfirms++ },
            onSettingsMessageShown = onSettingsMessageShown,
        )

    private fun render(
        settings: AppSettings?,
        now: Instant = Instant.parse("2026-10-03T12:00:00Z"),
    ) {
        composeRule.setContent {
            RepFlowTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = settings),
                    versionName = "0.1",
                    actions = actions(),
                    now = now,
                    zone = ZoneOffset.UTC,
                )
            }
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    @Test
    fun eachSwitchShowsTheStoredValueAndReportsItsOwnToggle() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_rest_auto_start)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.settings_rest_vibrate)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.settings_rest_notification)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.settings_keep_screen_awake)).performScrollTo().assertIsOff()
        composeRule.onNodeWithText(string(R.string.settings_confirm_finish)).performScrollTo().assertIsOn()

        composeRule.onNodeWithText(string(R.string.settings_keep_screen_awake)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_rest_vibrate)).performScrollTo().performClick()

        assertEquals(
            listOf(SettingToggle.KEEP_SCREEN_AWAKE to true, SettingToggle.REST_TIMER_VIBRATE to false),
            toggles,
        )
    }

    @Test
    fun switchesWaitDisabledUntilTheSettingsHaveLoaded() {
        render(settings = null)

        composeRule.onNodeWithText(string(R.string.settings_rest_auto_start)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_rest_auto_start)).performClick()
        assertEquals(emptyList<Pair<SettingToggle, Boolean>>(), toggles)
    }

    @Test
    fun theLibraryRowOpensTheLibrary() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_library_row)).performScrollTo().performClick()

        assertEquals(1, libraryClicks)
    }

    @Test
    fun eraseAllDataNeedsTheTypedConfirmation() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_erase_action)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.settings_erase_confirm_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_erase_confirm_action)).assertIsNotEnabled()

        composeRule
            .onNodeWithText(string(R.string.settings_erase_confirm_prompt, string(R.string.settings_erase_confirm_word)))
            .performTextInput(string(R.string.settings_erase_confirm_word))
        composeRule.onNodeWithText(string(R.string.settings_erase_confirm_action)).assertIsEnabled().performClick()

        assertEquals(1, eraseConfirms)
        composeRule.onNodeWithText(string(R.string.settings_erase_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun theGroupsFollowTheDesignOrder() {
        render(AppSettings.DEFAULT)
        val labels =
            listOf(
                R.string.settings_section_appearance,
                R.string.settings_section_rest_timer,
                R.string.settings_section_workout,
                R.string.settings_section_data,
            )

        // A scrolling column places every child, shown or not, so the order of their tops is the page order.
        val tops =
            labels.map { label ->
                composeRule.onNodeWithText(string(label).uppercase()).getUnclippedBoundsInRoot().top
            }

        assertEquals(tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
    }

    /** GF-4 (`8c`): Library, Archived and Backup and restore sit together under `Your data`, ahead of the Erase card. */
    @Test
    fun yourDataHoldsLibraryArchivedBackupAndThenTheEraseCard() {
        render(AppSettings.DEFAULT)

        val tops =
            listOf(
                string(R.string.settings_section_data).uppercase(),
                string(R.string.settings_library_row),
                string(R.string.settings_archived_row),
                string(R.string.settings_backup_row),
                string(R.string.settings_erase_action),
            ).map { composeRule.onNodeWithText(it).getUnclippedBoundsInRoot().top }

        assertEquals(tops.sorted(), tops)
        assertEquals(tops.size, tops.toSet().size)
        composeRule.onAllNodesWithText(string(R.string.settings_library_meta)).assertCountEquals(1)
    }

    @Test
    fun theArchivedRowIsCalledArchivedWithItsSubtitle() {
        render(AppSettings.DEFAULT)

        assertEquals("Archived", string(R.string.settings_archived_row))
        composeRule.onNodeWithText(string(R.string.settings_archived_row_meta)).performScrollTo().assertIsDisplayed()
        assertEquals("Exercises and plans", string(R.string.settings_archived_row_meta))
    }

    @Test
    fun theBackupRowSaysNoBackupYetBeforeTheFirstOne() {
        render(AppSettings.DEFAULT.copy(lastBackupAt = null))

        composeRule.onNodeWithText(string(R.string.backup_none_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBackupRowDatesTheLastBackupByTheBackupScreensLabelLogic() {
        val now = Instant.parse("2026-10-03T12:00:00Z")
        render(AppSettings.DEFAULT.copy(lastBackupAt = Instant.parse("2026-10-01T21:04:00Z")), now = now)

        composeRule.onNodeWithText(string(R.string.backup_last_days_ago, 2)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theBackupRowStaysBlankUntilTheSettingsHaveLoaded() {
        render(settings = null)

        composeRule.onAllNodesWithText(string(R.string.backup_none_title)).assertCountEquals(0)
    }

    @Test
    fun theRowCopyFollows8c() {
        render(AppSettings.DEFAULT)

        assertEquals("Used when neither the plan nor the exercise sets one", string(R.string.settings_default_rest_row_meta))
        composeRule.onNodeWithText(string(R.string.settings_default_rest_row_meta)).assertIsDisplayed()
        // Vibrate and Keep the screen on carry no subtitle: their rows hold the title and the switch only.
        composeRule.onNodeWithText(string(R.string.settings_rest_vibrate)).assertIsOn()
        composeRule.onAllNodesWithText("Even with the screen off").assertCountEquals(0)
        composeRule.onAllNodesWithText("Ends when you finish").assertCountEquals(0)
    }

    @Test
    fun theEraseCardNamesRecoveryEntriesAndSuggestions() {
        val body = string(R.string.settings_erase_body)

        assertTrue(body, body.contains("recovery entry"))
        assertTrue(body, body.contains("suggestion"))
        render(AppSettings.DEFAULT)
        composeRule.onNodeWithText(body).performScrollTo().assertIsDisplayed()
    }

    /** `8a` answer 11: the selected chip in the Default rest sheet carries a check; the Theme control (a segmented control) never does. */
    @Test
    fun theDefaultRestSheetsSelectedChipCarriesACheckAndTheThemeControlCarriesNone() {
        render(AppSettings.DEFAULT)
        composeRule.onAllNodesWithTag(CHOICE_CHECK_TAG, useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithText(string(R.string.settings_default_rest_row)).performClick()

        composeRule.onAllNodesWithTag(CHOICE_CHECK_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theExtraSetFieldsSheetsSelectedChoiceCarriesACheck() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_row)).performClick()

        composeRule.onAllNodesWithTag(CHOICE_CHECK_TAG, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theThemeShowsTheStoredChoiceAndReportsAnother() {
        render(AppSettings.DEFAULT.copy(theme = ThemeMode.LIGHT))

        composeRule.onNodeWithText(string(R.string.settings_theme_light)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).performClick()

        assertEquals(listOf(ThemeMode.DARK), themes)
    }

    @Test
    fun theDefaultRestRowShowsItsValueAndAPresetSavesAndCloses() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText("1:30").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_row)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_sheet_caption)).assertIsDisplayed()
        composeRule.onNodeWithText("3:00").performClick()

        assertEquals(listOf(180), rests)
        composeRule.onNodeWithText(string(R.string.settings_default_rest_sheet_caption)).assertDoesNotExist()
    }

    @Test
    fun otherOpensTheKeypadAndATypedRestIsSaved() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_default_rest_row)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_other)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_keypad_title)).assertIsDisplayed()
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("3").performClick()
        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithText(string(R.string.repflow_keypad_confirm)).performClick()

        assertEquals(listOf(135), rests)
        composeRule.onNodeWithText(string(R.string.settings_default_rest_sheet_caption)).assertDoesNotExist()
    }

    @Test
    fun aCustomRestStandsInOthersPlaceAsASelectedChip() {
        render(AppSettings.DEFAULT.copy(defaultRestSeconds = 135))

        composeRule.onNodeWithText("2:15").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_row)).performClick()

        composeRule.onAllNodesWithText("2:15").assertCountEquals(2)
        composeRule.onNodeWithText(string(R.string.settings_default_rest_other)).assertDoesNotExist()
    }

    @Test
    fun theExtraSetFieldsRowShowsItsModeAndTheSheetExplainsAndSavesAChoice() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_collapsed)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_row)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_always_meta)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_collapsed_meta)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_off_meta)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_off)).performClick()

        assertEquals(listOf(ExtraSetFields.OFF), extraFields)
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_off_meta)).assertDoesNotExist()
    }

    @Test
    fun theNewRowsWaitDisabledUntilTheSettingsHaveLoaded() {
        render(settings = null)

        composeRule.onNodeWithText(string(R.string.settings_theme_dark)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_default_rest_row)).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.settings_extra_set_fields_row)).performScrollTo().assertIsNotEnabled()
        assertEquals(emptyList<ThemeMode>(), themes)
    }

    @Test
    fun theArchivedRowOpensTheArchivedScreen() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_archived_row)).performScrollTo().performClick()

        assertEquals(1, archivedClicks)
    }

    @Test
    fun theBackupRowLeadsToTheBackupScreenAndTheBackupActionsAreNoLongerHere() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.settings_backup_row)).performScrollTo().performClick()

        assertEquals(1, backupClicks)
        composeRule.onNodeWithText(string(R.string.backup_export_action)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.backup_csv_export_action)).assertDoesNotExist()
    }

    /**
     * Remediation-1 CP16: a message is consumed only once its snackbar has been
     * shown. Consuming first cleared the key the showing effect is keyed on, and
     * the restart cancelled the snackbar before it was ever on screen - found by
     * the device run of the backup route tests.
     */
    @Test
    fun aMessageStaysOnScreenAfterItIsConsumed() {
        var settingsShown = 0
        composeRule.setContent {
            var uiState by remember { mutableStateOf(SettingsUiState(settings = AppSettings.DEFAULT, message = SettingsMessage.ERASED)) }
            RepFlowTheme {
                SettingsScreen(
                    uiState = uiState,
                    versionName = "0.1",
                    actions =
                        actions(
                            onSettingsMessageShown = {
                                settingsShown++
                                uiState = uiState.copy(message = null)
                            },
                        ),
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.settings_message_erased)).assertIsDisplayed()
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { settingsShown == 1 }
    }

    private companion object {
        const val SNACKBAR_WAIT_MILLIS = 10_000L
    }
}
