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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
@HiltViewModel
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
            viewModelScope.launch { loadForDate(today()) }
        }

        /** Loads (or resets to blank) the recovery/futsal fields for [date] (Milestone 8, CP3). */
        private suspend fun loadForDate(date: LocalDate) {
            val recovery = recoveryRepository.findForDate(date)
            val futsal = futsalRepository.findForDate(date)
            _uiState.update {
                RecoveryFutsalUiState(
                    isLoading = false,
                    date = date,
                    today = today(),
                    sleepQuality = recovery?.sleepQuality,
                    energy = recovery?.energy,
                    legDoms = recovery?.legDoms,
                    heelStiffness = recovery?.heelStiffness,
                    painWhileWalking = recovery?.painWhileWalking,
                    heavyLegs = recovery?.heavyLegs,
                    futsalInPrevious24h = recovery?.futsalInPrevious24h ?: false,
                    futsalExpectedNext24h = recovery?.futsalExpectedNext24h ?: false,
                    notes = recovery?.notes.orEmpty(),
                    durationMinutesInput = futsal?.durationMinutes?.toString().orEmpty(),
                    sessionRpeInput = futsal?.sessionRpe?.toString().orEmpty(),
                )
            }
        }

        private fun today(): LocalDate = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()

        /** Switches the screen to a different date (Milestone 8, CP3), reloading any entry already saved for it. */
        fun onDateChanged(date: LocalDate) {
            _uiState.update { it.copy(isLoading = true, date = date, isEntrySaved = false) }
            viewModelScope.launch { loadForDate(date) }
        }

        fun onScaleFieldChanged(
            field: RecoveryScaleField,
            value: Int,
        ) {
            val clamped = value.coerceIn(RecoveryFutsalUiState.SCALE_MIN, RecoveryFutsalUiState.SCALE_MAX)
            edit { state ->
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

        fun onFutsalPreviousToggled(value: Boolean) = edit { it.copy(futsalInPrevious24h = value) }

        fun onFutsalNextToggled(value: Boolean) = edit { it.copy(futsalExpectedNext24h = value) }

        fun onNotesChanged(value: String) = edit { it.copy(notes = value) }

        fun onDurationChanged(value: String) = edit { it.copy(durationMinutesInput = value) }

        fun onSessionRpeChanged(value: String) = edit { it.copy(sessionRpeInput = value) }

        /** Any edit makes the entry unsaved again, so the bar reads `Save entry` (`3c`). */
        private fun edit(change: (RecoveryFutsalUiState) -> RecoveryFutsalUiState) {
            _uiState.update { change(it).copy(isEntrySaved = false) }
        }

        /**
         * `3c`'s one `Save entry` (remediation-1 CP13): saves the check-in and,
         * while "Played in last 24h" is on, the futsal session for the same date.
         *
         * The futsal fields are optional: with both empty only the check-in is
         * saved, as the old screen's separate futsal save allowed. One filled
         * without the other, or a non-number, is rejected before anything is
         * written. A check-in saved before a futsal failure stays saved, and
         * saving again rewrites both (each repository upserts by date).
         */
        fun onSaveEntry() {
            val state = _uiState.value
            if (state.isSaving || state.isLoading || !state.hasAllScaleValues) return
            val futsal = if (state.futsalInPrevious24h) state.futsalInput() else FutsalInput.None
            if (futsal is FutsalInput.Invalid) {
                _uiState.update { it.copy(errorMessage = MESSAGE_INVALID) }
                return
            }
            _uiState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val errorKey = saveRecovery(state) ?: (futsal as? FutsalInput.Session)?.let { saveFutsal(state.date, it) }
                _uiState.update {
                    if (errorKey == null) {
                        it.copy(isSaving = false, isEntrySaved = true)
                    } else {
                        it.copy(isSaving = false, errorMessage = errorKey)
                    }
                }
            }
        }

        /** Returns the error message key, or null on success. */
        private suspend fun saveRecovery(state: RecoveryFutsalUiState): String? {
            val result =
                recordRecoveryEntry(
                    RecordRecoveryEntryCommand(
                        date = state.date,
                        sleepQuality = requireNotNull(state.sleepQuality),
                        energy = requireNotNull(state.energy),
                        legDoms = requireNotNull(state.legDoms),
                        heelStiffness = requireNotNull(state.heelStiffness),
                        painWhileWalking = requireNotNull(state.painWhileWalking),
                        heavyLegs = requireNotNull(state.heavyLegs),
                        futsalInPrevious24h = state.futsalInPrevious24h,
                        futsalExpectedNext24h = state.futsalExpectedNext24h,
                        notes = state.notes.ifBlank { null },
                    ),
                )
            return when (result) {
                is DomainResult.Success -> null
                is DomainResult.Failure -> result.error.toMessageKey()
            }
        }

        /** Returns the error message key, or null on success. */
        private suspend fun saveFutsal(
            date: LocalDate,
            session: FutsalInput.Session,
        ): String? {
            val result =
                recordFutsalSession(
                    RecordFutsalSessionCommand(date = date, durationMinutes = session.durationMinutes, sessionRpe = session.sessionRpe),
                )
            return when (result) {
                is DomainResult.Success -> null
                is DomainResult.Failure -> result.error.toMessageKey()
            }
        }

        fun onMessageShown() {
            _uiState.update { it.copy(errorMessage = null) }
        }

        private fun RecoveryOperationError.toMessageKey(): String =
            when (this) {
                is RecoveryOperationError.ValidationFailed -> MESSAGE_INVALID
                RecoveryOperationError.PersistenceUnavailable -> MESSAGE_UNAVAILABLE
            }

        private fun FutsalOperationError.toMessageKey(): String =
            when (this) {
                is FutsalOperationError.ValidationFailed -> MESSAGE_INVALID
                FutsalOperationError.PersistenceUnavailable -> MESSAGE_UNAVAILABLE
            }

        companion object {
            /** The error keys [RecoveryFutsalScreen] maps to copy. */
            const val MESSAGE_INVALID = "invalid"
            const val MESSAGE_UNAVAILABLE = "unavailable"
        }
    }

/** What the futsal block holds when the entry is saved. */
private sealed interface FutsalInput {
    /** Both fields empty: no session to save. */
    data object None : FutsalInput

    /** One field empty, or not a number. */
    data object Invalid : FutsalInput

    data class Session(
        val durationMinutes: Int,
        val sessionRpe: Double,
    ) : FutsalInput
}

private fun RecoveryFutsalUiState.futsalInput(): FutsalInput {
    if (durationMinutesInput.isBlank() && sessionRpeInput.isBlank()) return FutsalInput.None
    val duration = durationMinutesInput.trim().toIntOrNull()
    val rpe = sessionRpeInput.trim().toDoubleOrNull()
    return if (duration == null || rpe == null) FutsalInput.Invalid else FutsalInput.Session(duration, rpe)
}
