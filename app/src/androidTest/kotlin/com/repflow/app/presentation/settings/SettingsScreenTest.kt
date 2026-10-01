package com.repflow.app.presentation.settings

import androidx.activity.ComponentActivity
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
}
