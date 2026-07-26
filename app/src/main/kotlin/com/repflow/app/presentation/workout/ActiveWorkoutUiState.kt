package com.repflow.app.presentation.workout

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import java.time.Instant

/**
 * Stable UI state for the current-workout screen (Milestone 3 CP6+CP7).
 * [availableExercises] powers the "add exercise" picker; it is populated
 * independently of [content] so it stays available even before a session is
 * started.
 */
data class ActiveWorkoutUiState(
    val content: ActiveWorkoutContent = ActiveWorkoutContent.Loading,
    val availableExercises: List<ExercisePickerItem> = emptyList(),
    val errorMessage: ActiveWorkoutErrorReason? = null,
)

data class ExercisePickerItem(
    val id: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
)

sealed interface ActiveWorkoutContent {
    data object Loading : ActiveWorkoutContent

    data object NoActiveSession : ActiveWorkoutContent

    data class Active(
        val sessionId: WorkoutSessionId,
        val startedAt: Instant,
        val exercises: List<ActiveExerciseUi>,
        val restTimer: RestTimerUi? = null,
    ) : ActiveWorkoutContent

    data class ObservationFailed(
        val reason: ActiveWorkoutErrorReason,
    ) : ActiveWorkoutContent
}

data class ActiveExerciseUi(
    val id: WorkoutExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val sets: List<ActiveSetUi>,
)

data class ActiveSetUi(
    val id: WorkoutSetId,
    val setNumber: Int,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
)

/** Presentation carries the absolute end timestamp; the Composable derives remaining time via its own 1s tick, so a full recomposition after process death reconstructs the correct value with no drift. */
data class RestTimerUi(
    val endAt: Instant,
    val totalDurationSeconds: Int,
)

/** The specific failure cause is deliberately not surfaced verbatim to the user, mirroring the list-screen conventions elsewhere in the app. */
enum class ActiveWorkoutErrorReason {
    ALREADY_ACTIVE,
    NOT_FOUND,
    VALIDATION_FAILED,
    PERSISTENCE_UNAVAILABLE,
    UNKNOWN,
}
