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
import com.repflow.app.presentation.backup.BackupStatusMessage
import com.repflow.app.presentation.backup.BackupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings, converted to `5c` (remediation-1-remediation-1 CP7), grouped as the
 * design groups it: each switch renders the stored value and reports its own
 * toggle; `Theme`, `Default rest` and `Extra set fields` render the stored
 * value and report their own choice (the two rows through their sheets); all
 * of it waits disabled until the settings have loaded; the Library and
 * Archived rows lead where they say; and `Erase all data` sits behind a typed
 * confirmation. The Data group's three backup rows stay until CP8's Backup
 * screen replaces them.
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
    private var eraseConfirms = 0

    private fun actions(
        onBackupStatusShown: () -> Unit = {},
        onSettingsMessageShown: () -> Unit = {},
    ) = SettingsActions(
        onBack = {},
        onLibraryClick = { libraryClicks++ },
        onArchivedClick = { archivedClicks++ },
        onToggle = { toggle, on -> toggles += toggle to on },
        onThemeSelected = { themes += it },
        onDefaultRestSelected = { rests += it },
        onExtraSetFieldsSelected = { extraFields += it },
        onExportBackup = {},
        onRestoreBackup = {},
        onExportCsv = {},
        onRestoreConfirmed = {},
        onRestoreCancelled = {},
        onBackupStatusShown = onBackupStatusShown,
        onEraseAllDataConfirmed = { eraseConfirms++ },
        onSettingsMessageShown = onSettingsMessageShown,
    )

    private fun render(settings: AppSettings?) {
        composeRule.setContent {
            RepFlowTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = settings),
                    backupState = BackupUiState(),
                    versionName = "0.1",
                    actions = actions(),
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

        composeRule.onNodeWithText(string(R.string.settings_library_row)).performClick()

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
                R.string.settings_section_units_appearance,
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
    fun theDataGroupKeepsTheThreeBackupRowsUntilTheBackupScreenReplacesThem() {
        render(AppSettings.DEFAULT)

        composeRule.onNodeWithText(string(R.string.backup_export_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_csv_export_action)).performScrollTo().assertIsDisplayed()
    }

    /**
     * Remediation-1 CP16: both messages are consumed only once their snackbar
     * has been shown. Consuming first cleared the key the showing effect is
     * keyed on, and the restart cancelled the snackbar before it was ever on
     * screen - found by the device run of the backup route tests.
     */
    @Test
    fun aMessageStaysOnScreenAfterItIsConsumed() {
        var backupShown = 0
        var settingsShown = 0
        composeRule.setContent {
            var backupState by remember { mutableStateOf(BackupUiState(statusMessage = BackupStatusMessage.OperationFailed)) }
            var uiState by remember { mutableStateOf(SettingsUiState(settings = AppSettings.DEFAULT)) }
            RepFlowTheme {
                SettingsScreen(
                    uiState = uiState,
                    backupState = backupState,
                    versionName = "0.1",
                    actions =
                        actions(
                            onBackupStatusShown = {
                                backupShown++
                                backupState = backupState.copy(statusMessage = null)
                                uiState = uiState.copy(message = SettingsMessage.ERASED)
                            },
                            onSettingsMessageShown = {
                                settingsShown++
                                uiState = uiState.copy(message = null)
                            },
                        ),
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.backup_message_operation_failed)).assertIsDisplayed()
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { backupShown == 1 }
        composeRule.onNodeWithText(string(R.string.settings_message_erased)).assertIsDisplayed()
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { settingsShown == 1 }
    }

    private companion object {
        const val SNACKBAR_WAIT_MILLIS = 10_000L
    }
}
