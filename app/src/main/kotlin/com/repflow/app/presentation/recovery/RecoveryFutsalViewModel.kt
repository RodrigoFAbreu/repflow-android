package com.repflow.app.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.common.Clock
import com.repflow.app.application.recovery.FutsalOperationError
import com.repflow.app.application.recovery.FutsalRepository
import com.repflow.app.application.recovery.RecordFutsalSession
import com.repflow.app.application.recovery.RecordFutsalSessionCommand
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.RecordRecoveryEntryCommand
import com.repflow.app.application.recovery.RecoveryOperationError
import com.repflow.app.application.recovery.RecoveryRepository
import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Owns the combined recovery-entry / futsal-session screen state, both
 * scoped to "today" (see `docs/milestones/completed/milestone-5-reference.md`).
 *
 * Kept as a single ViewModel/screen rather than two separate destinations
 * to reduce the vertical slice's scope while still covering both flows from
 * `docs/UX_FLOWS.md`'s "Recovery entry" section.
 */
@Suppress("TooManyFunctions")
class RecoveryFutsalViewModel
    @Inject
    constructor(
        private val recoveryRepository: RecoveryRepository,
        private val futsalRepository: FutsalRepository,
        private val recordRecoveryEntry: RecordRecoveryEntry,
        private val recordFutsalSession: RecordFutsalSession,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecoveryFutsalUiState())
        val uiState = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
                val recovery = recoveryRepository.findForDate(today)
                val futsal = futsalRepository.findForDate(today)
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        sleepQuality = recovery?.sleepQuality ?: state.sleepQuality,
                        energy = recovery?.energy ?: state.energy,
                        legDoms = recovery?.legDoms ?: state.legDoms,
                        heelStiffness = recovery?.heelStiffness ?: state.heelStiffness,
                        painWhileWalking = recovery?.painWhileWalking ?: state.painWhileWalking,
                        heavyLegs = recovery?.heavyLegs ?: state.heavyLegs,
                        futsalInPrevious24h = recovery?.futsalInPrevious24h ?: state.futsalInPrevious24h,
                        futsalExpectedNext24h = recovery?.futsalExpectedNext24h ?: state.futsalExpectedNext24h,
                        notes = recovery?.notes.orEmpty(),
                        durationMinutesInput = futsal?.durationMinutes?.toString().orEmpty(),
                        sessionRpeInput = futsal?.sessionRpe?.toString().orEmpty(),
                    )
                }
            }
        }

        fun onScaleFieldChanged(
            field: RecoveryScaleField,
            value: Int,
        ) {
            val clamped = value.coerceIn(RecoveryFutsalUiState.SCALE_MIN, RecoveryFutsalUiState.SCALE_MAX)
            _uiState.update { state ->
                when (field) {
                    RecoveryScaleField.SLEEP_QUALITY -> state.copy(sleepQuality = clamped)
                    RecoveryScaleField.ENERGY -> state.copy(energy = clamped)
                    RecoveryScaleField.LEG_DOMS -> state.copy(legDoms = clamped)
                    RecoveryScaleField.HEEL_STIFFNESS -> state.copy(heelStiffness = clamped)
                    RecoveryScaleField.PAIN_WHILE_WALKING -> state.copy(painWhileWalking = clamped)
                    RecoveryScaleField.HEAVY_LEGS -> state.copy(heavyLegs = clamped)
                }
            }
        }

        fun onFutsalPreviousToggled(value: Boolean) {
            _uiState.update { it.copy(futsalInPrevious24h = value) }
        }

        fun onFutsalNextToggled(value: Boolean) {
            _uiState.update { it.copy(futsalExpectedNext24h = value) }
        }

        fun onNotesChanged(value: String) {
            _uiState.update { it.copy(notes = value) }
        }

        fun onDurationChanged(value: String) {
            _uiState.update { it.copy(durationMinutesInput = value) }
        }

        fun onSessionRpeChanged(value: String) {
            _uiState.update { it.copy(sessionRpeInput = value) }
        }

        fun onSaveRecovery() {
            val state = _uiState.value
            viewModelScope.launch {
                val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
                val result =
                    recordRecoveryEntry(
                        RecordRecoveryEntryCommand(
                            date = today,
                            sleepQuality = state.sleepQuality,
                            energy = state.energy,
                            legDoms = state.legDoms,
                            heelStiffness = state.heelStiffness,
                            painWhileWalking = state.painWhileWalking,
                            heavyLegs = state.heavyLegs,
                            futsalInPrevious24h = state.futsalInPrevious24h,
                            futsalExpectedNext24h = state.futsalExpectedNext24h,
                            notes = state.notes.ifBlank { null },
                        ),
                    )
                when (result) {
                    is DomainResult.Success -> _uiState.update { it.copy(recoverySavedMessage = "saved") }
                    is DomainResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toMessageKey()) }
                }
            }
        }

        fun onSaveFutsal() {
            val state = _uiState.value
            val durationMinutes = state.durationMinutesInput.toIntOrNull()
            val sessionRpe = state.sessionRpeInput.toDoubleOrNull()
            if (durationMinutes == null || sessionRpe == null) {
                _uiState.update { it.copy(errorMessage = "invalid") }
                return
            }
            viewModelScope.launch {
                val today = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
                val result =
                    recordFutsalSession(
                        RecordFutsalSessionCommand(date = today, durationMinutes = durationMinutes, sessionRpe = sessionRpe),
                    )
                when (result) {
                    is DomainResult.Success -> _uiState.update { it.copy(futsalSavedMessage = "saved") }
                    is DomainResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.toMessageKey()) }
                }
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(recoverySavedMessage = null, futsalSavedMessage = null, errorMessage = null) }
        }

        private fun RecoveryOperationError.toMessageKey(): String =
            when (this) {
                is RecoveryOperationError.ValidationFailed -> "invalid"
                RecoveryOperationError.PersistenceUnavailable -> "unavailable"
            }

        private fun FutsalOperationError.toMessageKey(): String =
            when (this) {
                is FutsalOperationError.ValidationFailed -> "invalid"
                FutsalOperationError.PersistenceUnavailable -> "unavailable"
            }
    }
