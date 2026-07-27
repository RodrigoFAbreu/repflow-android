package com.repflow.app.presentation.recovery

import java.time.LocalDate

/** Identifies which 0-5 recovery scale field a stepper change applies to. */
enum class RecoveryScaleField {
    SLEEP_QUALITY,
    ENERGY,
    LEG_DOMS,
    HEEL_STIFFNESS,
    PAIN_WHILE_WALKING,
    HEAVY_LEGS,
}

/**
 * UI state for the combined recovery-entry / futsal-session screen.
 *
 * [date] defaults to the wall-clock "now" only as a placeholder before the
 * ViewModel's `init` overwrites it with its injected [com.repflow.app.application.common.Clock]'s
 * value (Milestone 8, CP3) - mirrors how [isLoading] starts `true` and is
 * immediately corrected once real data loads.
 */
data class RecoveryFutsalUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val sleepQuality: Int = DEFAULT_SCALE_VALUE,
    val energy: Int = DEFAULT_SCALE_VALUE,
    val legDoms: Int = DEFAULT_SCALE_VALUE,
    val heelStiffness: Int = DEFAULT_SCALE_VALUE,
    val painWhileWalking: Int = DEFAULT_SCALE_VALUE,
    val heavyLegs: Int = DEFAULT_SCALE_VALUE,
    val futsalInPrevious24h: Boolean = false,
    val futsalExpectedNext24h: Boolean = false,
    val notes: String = "",
    val durationMinutesInput: String = "",
    val sessionRpeInput: String = "",
    val isSavingRecovery: Boolean = false,
    val isSavingFutsal: Boolean = false,
    val recoverySavedMessage: String? = null,
    val futsalSavedMessage: String? = null,
    val errorMessage: String? = null,
) {
    val futsalLoad: Double?
        get() {
            val duration = durationMinutesInput.toIntOrNull() ?: return null
            val rpe = sessionRpeInput.toDoubleOrNull() ?: return null
            return duration * rpe
        }

    companion object {
        const val DEFAULT_SCALE_VALUE = 2
        const val SCALE_MIN = 0

        /** Milestone 8, CP3: widened from 4 to match [com.repflow.app.domain.recovery.RecoveryEntry]'s 0..5 range. */
        const val SCALE_MAX = 5
    }
}
