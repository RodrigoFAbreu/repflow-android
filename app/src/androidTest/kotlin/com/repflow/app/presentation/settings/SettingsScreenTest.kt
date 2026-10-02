package com.repflow.app.presentation.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.backup.BackupStatusMessage
import com.repflow.app.presentation.backup.BackupUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Remediation-1 CP14's Settings screen (`4a`): each switch renders the stored
 * value and reports its own toggle, the switches wait disabled until the
 * settings have loaded, the Library row leads to the library, and `Erase all
 * data` sits behind a typed confirmation.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val toggles = mutableListOf<Pair<SettingToggle, Boolean>>()
    private var libraryClicks = 0
    private var eraseConfirms = 0

    private fun render(settings: AppSettings?) {
        composeRule.setContent {
            RepFlowTheme {
                SettingsScreen(
                    uiState = SettingsUiState(settings = settings),
                    backupState = BackupUiState(),
                    versionName = "0.1",
                    actions =
                        SettingsActions(
                            onBack = {},
                            onLibraryClick = { libraryClicks++ },
                            onToggle = { toggle, on -> toggles += toggle to on },
                            onExportBackup = {},
                            onRestoreBackup = {},
                            onExportCsv = {},
                            onRestoreConfirmed = {},
                            onRestoreCancelled = {},
                            onBackupStatusShown = {},
                            onEraseAllDataConfirmed = { eraseConfirms++ },
                            onSettingsMessageShown = {},
                        ),
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
                        SettingsActions(
                            onBack = {},
                            onLibraryClick = {},
                            onToggle = { _, _ -> },
                            onExportBackup = {},
                            onRestoreBackup = {},
                            onExportCsv = {},
                            onRestoreConfirmed = {},
                            onRestoreCancelled = {},
                            onBackupStatusShown = {
                                backupShown++
                                backupState = backupState.copy(statusMessage = null)
                                uiState = uiState.copy(message = SettingsMessage.ERASED)
                            },
                            onEraseAllDataConfirmed = {},
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
