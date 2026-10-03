package com.repflow.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.backup.EraseAllData
import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.settings.SettingsRepository
import com.repflow.app.application.settings.ThemeMode
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.presentation.workout.RestNotificationCanceller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of Settings' five switches (remediation-1 CP14), in the order the screen draws them. */
enum class SettingToggle {
    REST_TIMER_AUTO_START,
    REST_TIMER_VIBRATE,
    REST_TIMER_NOTIFICATION,
    KEEP_SCREEN_AWAKE,
    CONFIRM_BEFORE_FINISHING,
}

/** Whether [toggle] is on in these settings. */
fun AppSettings.isOn(toggle: SettingToggle): Boolean =
    when (toggle) {
        SettingToggle.REST_TIMER_AUTO_START -> restTimerAutoStart
        SettingToggle.REST_TIMER_VIBRATE -> restTimerVibrate
        SettingToggle.REST_TIMER_NOTIFICATION -> restTimerNotification
        SettingToggle.KEEP_SCREEN_AWAKE -> keepScreenAwake
        SettingToggle.CONFIRM_BEFORE_FINISHING -> confirmBeforeFinishing
    }

/** These settings with [toggle] set to [on] and every other switch unchanged. */
fun AppSettings.withToggle(
    toggle: SettingToggle,
    on: Boolean,
): AppSettings =
    when (toggle) {
        SettingToggle.REST_TIMER_AUTO_START -> copy(restTimerAutoStart = on)
        SettingToggle.REST_TIMER_VIBRATE -> copy(restTimerVibrate = on)
        SettingToggle.REST_TIMER_NOTIFICATION -> copy(restTimerNotification = on)
        SettingToggle.KEEP_SCREEN_AWAKE -> copy(keepScreenAwake = on)
        SettingToggle.CONFIRM_BEFORE_FINISHING -> copy(confirmBeforeFinishing = on)
    }

/** A one-off outcome Settings reports in its snackbar. */
enum class SettingsMessage { SAVE_FAILED, ERASED, ERASE_FAILED }

/**
 * Settings' state: [settings] is `null` until the repository first emits, so
 * no switch ever shows a value it does not have.
 */
data class SettingsUiState(
    val settings: AppSettings? = null,
    val isErasing: Boolean = false,
    val message: SettingsMessage? = null,
)

/**
 * Drives Settings' switches from [SettingsRepository] and runs `Erase all data`
 * through [EraseAllData] (remediation-1 CP14). The backup rows beside them are
 * [com.repflow.app.presentation.backup.BackupViewModel]'s, unchanged.
 *
 * Each switch writes straight through; the switch then shows what the
 * repository re-emits, so what is drawn is always what is stored.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val eraseAllData: EraseAllData,
        private val restNotificationCanceller: RestNotificationCanceller,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                settingsRepository
                    .observe()
                    .catch { failure -> if (failure is CancellationException) throw failure }
                    .collect { settings -> _uiState.update { it.copy(settings = settings) } }
            }
        }

        fun onToggle(
            toggle: SettingToggle,
            on: Boolean,
        ) = write { it.withToggle(toggle, on) }

        /** `Theme`'s segment: stores [mode] and nothing else. */
        fun onThemeSelected(mode: ThemeMode) = write { it.copy(theme = mode) }

        /**
         * `Default rest`'s sheet: stores [seconds] clamped to the exercise rest's own 1-1800 range,
         * so `Other` can never store a rest no exercise could have.
         */
        fun onDefaultRestSelected(seconds: Int) =
            write { it.copy(defaultRestSeconds = seconds.coerceIn(MIN_DEFAULT_REST_SECONDS, MAX_DEFAULT_REST_SECONDS)) }

        /** `Extra set fields`' sheet: stores [mode] and nothing else. */
        fun onExtraSetFieldsSelected(mode: ExtraSetFields) = write { it.copy(extraSetFields = mode) }

        private fun write(transform: (AppSettings) -> AppSettings) {
            viewModelScope.launch {
                when (settingsRepository.update(transform)) {
                    is DomainResult.Success -> Unit
                    is DomainResult.Failure -> _uiState.update { it.copy(message = SettingsMessage.SAVE_FAILED) }
                }
            }
        }

        /** The typed confirmation's destructive action. A second confirm while one is running is ignored. */
        fun onEraseAllDataConfirmed() {
            if (_uiState.value.isErasing) return
            _uiState.update { it.copy(isErasing = true) }
            viewModelScope.launch {
                val message =
                    when (eraseAllData()) {
                        is DomainResult.Success -> {
                            restNotificationCanceller.cancel()
                            SettingsMessage.ERASED
                        }

                        is DomainResult.Failure -> {
                            SettingsMessage.ERASE_FAILED
                        }
                    }
                _uiState.update { it.copy(isErasing = false, message = message) }
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(message = null) }
        }

        private companion object {
            const val MIN_DEFAULT_REST_SECONDS = 1
            const val MAX_DEFAULT_REST_SECONDS = 1_800
        }
    }
