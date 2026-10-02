package com.repflow.app.presentation.recovery

import java.time.LocalDate

/**
 * UI state for the combined recovery-entry / futsal-session screen.
 *
 * [date] defaults to the wall-clock "now" only as a placeholder before the
 * ViewModel's `init` overwrites it with its injected [com.repflow.app.application.common.Clock]'s
 * value (Milestone 8, CP3) - mirrors how [isLoading] starts `true` and is
 * immediately corrected once real data loads.
 *
 * Remediation-1 CP13 (`3c`): one `Save entry` saves the check-in and, while
 * [futsalInPrevious24h] is on, the futsal session ([isSaving] guards it);
 * [isEntrySaved] turns the button into `Saved` until the next edit. [today]
 * is the injected clock's date at load, so the date row can read
 * `Today · 11 Aug 2026`.
 */
data class RecoveryFutsalUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate? = null,
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val legDoms: Int? = null,
    val heelStiffness: Int? = null,
    val painWhileWalking: Int? = null,
    val heavyLegs: Int? = null,
    val futsalInPrevious24h: Boolean = false,
    val futsalExpectedNext24h: Boolean = false,
    val notes: String = "",
    val durationMinutesInput: String = "",
    val sessionRpeInput: String = "",
    val isSaving: Boolean = false,
    val isEntrySaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val futsalLoad: Double?
        get() {
            val duration = durationMinutesInput.toIntOrNull() ?: return null
            val rpe = sessionRpeInput.toDoubleOrNull() ?: return null
            return duration * rpe
        }

    /**
     * Every scale holds a value the user chose (or one loaded from a saved
     * entry). A day with nothing logged starts with all six unset, so `Save
     * entry` can never record values nobody picked.
     */
    val hasAllScaleValues: Boolean
        get() = RecoveryScaleField.entries.all { valueOf(it) != null }

    /** The value currently entered on [field]'s 0-5 scale, or null while the user has not chosen one. */
    fun valueOf(field: RecoveryScaleField): Int? =
        when (field) {
            RecoveryScaleField.SLEEP_QUALITY -> sleepQuality
            RecoveryScaleField.ENERGY -> energy
            RecoveryScaleField.LEG_DOMS -> legDoms
            RecoveryScaleField.HEEL_STIFFNESS -> heelStiffness
            RecoveryScaleField.PAIN_WHILE_WALKING -> painWhileWalking
            RecoveryScaleField.HEAVY_LEGS -> heavyLegs
        }

    companion object {
        const val SCALE_MIN = 0

        /** Milestone 8, CP3: widened from 4 to match [com.repflow.app.domain.recovery.RecoveryEntry]'s 0..5 range. */
        const val SCALE_MAX = 5
    }
}
