package com.repflow.app.presentation.workout

import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.Instant

/**
 * Stable UI state for the current-workout screen (Milestone 3 CP6). Set
 * entry/editing is CP7 scope - this checkpoint only covers observing
 * whether a session is active and starting/completing/abandoning it.
 */
data class ActiveWorkoutUiState(
    val content: ActiveWorkoutContent = ActiveWorkoutContent.Loading,
    val errorMessage: ActiveWorkoutErrorReason? = null,
)

sealed interface ActiveWorkoutContent {
    data object Loading : ActiveWorkoutContent

    data object NoActiveSession : ActiveWorkoutContent

    data class Active(
        val sessionId: WorkoutSessionId,
        val startedAt: Instant,
        val exerciseCount: Int,
        val setCount: Int,
    ) : ActiveWorkoutContent

    data class ObservationFailed(
        val reason: ActiveWorkoutErrorReason,
    ) : ActiveWorkoutContent
}

/** The specific failure cause is deliberately not surfaced verbatim to the user, mirroring the list-screen conventions elsewhere in the app. */
enum class ActiveWorkoutErrorReason {
    ALREADY_ACTIVE,
    NOT_FOUND,
    VALIDATION_FAILED,
    PERSISTENCE_UNAVAILABLE,
    UNKNOWN,
}
