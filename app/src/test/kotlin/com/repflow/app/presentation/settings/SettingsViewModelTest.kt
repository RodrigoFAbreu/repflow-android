package com.repflow.app.presentation.settings

import com.repflow.app.application.backup.EraseAllData
import com.repflow.app.application.backup.InMemoryTrainingDataRepository
import com.repflow.app.application.backup.TrainingDataError
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.InMemorySettingsRepository
import com.repflow.app.application.settings.SettingsPersistenceError
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.presentation.workout.RecordingRestNotificationCanceller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val settingsRepository = InMemorySettingsRepository()
    private val trainingData = InMemoryTrainingDataRepository()
    private val restNotificationCanceller = RecordingRestNotificationCanceller()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(settingsRepository, EraseAllData(trainingData), restNotificationCanceller)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the switches show the stored settings`() {
        assertEquals(AppSettings.DEFAULT, viewModel.uiState.value.settings)
    }

    @Test
    fun `each switch writes only its own preference`() =
        runTest {
            for (toggle in SettingToggle.entries) {
                val before = settingsRepository.get()
                val on = !before.isOn(toggle)

                viewModel.onToggle(toggle, on)

                val after = settingsRepository.get()
                assertEquals(on, after.isOn(toggle))
                SettingToggle.entries.filter { it != toggle }.forEach { other ->
                    assertEquals("$toggle changed $other", before.isOn(other), after.isOn(other))
                }
                assertEquals(after, viewModel.uiState.value.settings)
            }
        }

    @Test
    fun `a failed write reports it and leaves the stored value`() =
        runTest {
            settingsRepository.nextUpdateFailure = SettingsPersistenceError.Unavailable

            viewModel.onToggle(SettingToggle.KEEP_SCREEN_AWAKE, true)

            assertEquals(SettingsMessage.SAVE_FAILED, viewModel.uiState.value.message)
            assertFalse(settingsRepository.get().keepScreenAwake)
            viewModel.onMessageShown()
            assertEquals(null, viewModel.uiState.value.message)
        }

    @Test
    fun `the theme writes only itself`() =
        runTest {
            val before = settingsRepository.get()

            viewModel.onThemeSelected(ThemeMode.DARK)

            assertEquals(before.copy(theme = ThemeMode.DARK), settingsRepository.get())
            assertEquals(
                ThemeMode.DARK,
                viewModel.uiState.value.settings
                    ?.theme,
            )
        }

    @Test
    fun `the default rest writes only itself`() =
        runTest {
            val before = settingsRepository.get()

            viewModel.onDefaultRestSelected(135)

            assertEquals(before.copy(defaultRestSeconds = 135), settingsRepository.get())
            assertEquals(
                135,
                viewModel.uiState.value.settings
                    ?.defaultRestSeconds,
            )
        }

    @Test
    fun `a typed default rest outside 1 to 1800 seconds is clamped into range`() =
        runTest {
            viewModel.onDefaultRestSelected(0)
            assertEquals(1, settingsRepository.get().defaultRestSeconds)

            viewModel.onDefaultRestSelected(999_999)
            assertEquals(1_800, settingsRepository.get().defaultRestSeconds)
        }

    @Test
    fun `the extra set fields mode writes only itself`() =
        runTest {
            val before = settingsRepository.get()

            viewModel.onExtraSetFieldsSelected(ExtraSetFields.OFF)

            assertEquals(before.copy(extraSetFields = ExtraSetFields.OFF), settingsRepository.get())
            assertEquals(
                ExtraSetFields.OFF,
                viewModel.uiState.value.settings
                    ?.extraSetFields,
            )
        }

    @Test
    fun `each new setting reports a failed write and keeps the stored value`() =
        runTest {
            val writes =
                listOf<() -> Unit>(
                    { viewModel.onThemeSelected(ThemeMode.LIGHT) },
                    { viewModel.onDefaultRestSelected(180) },
                    { viewModel.onExtraSetFieldsSelected(ExtraSetFields.ALWAYS_SHOWN) },
                )
            for (write in writes) {
                settingsRepository.nextUpdateFailure = SettingsPersistenceError.Unavailable

                write()

                assertEquals(SettingsMessage.SAVE_FAILED, viewModel.uiState.value.message)
                assertEquals(AppSettings.DEFAULT, settingsRepository.get())
                viewModel.onMessageShown()
            }
        }

    @Test
    fun `a rest shows as minutes and seconds`() {
        assertEquals("1:00", formatRest(60))
        assertEquals("1:30", formatRest(90))
        assertEquals("2:15", formatRest(135))
        assertEquals("0:05", formatRest(5))
        assertEquals("30:00", formatRest(1_800))
    }

    @Test
    fun `erasing all data clears the training data and keeps the settings`() =
        runTest {
            viewModel.onToggle(SettingToggle.CONFIRM_BEFORE_FINISHING, false)

            viewModel.onEraseAllDataConfirmed()

            assertEquals(1, trainingData.clearCount)
            assertEquals(1, restNotificationCanceller.cancelCount)
            assertEquals(SettingsMessage.ERASED, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.isErasing)
            assertFalse(settingsRepository.get().confirmBeforeFinishing)
        }

    @Test
    fun `a failed erase is reported`() =
        runTest {
            trainingData.nextClearFailure = TrainingDataError.Unavailable

            viewModel.onEraseAllDataConfirmed()

            assertEquals(SettingsMessage.ERASE_FAILED, viewModel.uiState.value.message)
            assertEquals(0, restNotificationCanceller.cancelCount)
            assertFalse(viewModel.uiState.value.isErasing)
        }

    @Test
    fun `the typed confirmation accepts the word only`() {
        assertTrue(isEraseConfirmationTyped("ERASE", "ERASE"))
        assertTrue(isEraseConfirmationTyped("  erase ", "ERASE"))
        assertFalse(isEraseConfirmationTyped("", "ERASE"))
        assertFalse(isEraseConfirmationTyped("ERAS", "ERASE"))
        assertFalse(isEraseConfirmationTyped("ERASE ALL", "ERASE"))
    }

    @Test
    fun `the defaults keep the behaviour from before Settings existed`() {
        val defaults = AppSettings.DEFAULT
        assertTrue(defaults.restTimerAutoStart)
        assertTrue(defaults.restTimerVibrate)
        assertTrue(defaults.restTimerNotification)
        assertFalse(defaults.keepScreenAwake)
        assertTrue(defaults.confirmBeforeFinishing)
    }
}
