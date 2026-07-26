package com.repflow.app.presentation.recovery

/** Identifies which 0-4 recovery scale field a stepper change applies to. */
enum class RecoveryScaleField {
    SLEEP_QUALITY,
    ENERGY,
    LEG_DOMS,
    HEEL_STIFFNESS,
    PAIN_WHILE_WALKING,
    HEAVY_LEGS,
}

/** UI state for the combined recovery-entry / futsal-session screen, both scoped to "today". */
data class RecoveryFutsalUiState(
    val isLoading: Boolean = true,
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
        const val SCALE_MAX = 4
    }
}
