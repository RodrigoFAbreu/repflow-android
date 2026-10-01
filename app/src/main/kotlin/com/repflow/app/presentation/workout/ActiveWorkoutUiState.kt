package com.repflow.app.presentation.workout

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import java.math.BigDecimal
import java.time.Instant

/**
 * Stable UI state for the current-workout screen (Milestone 3 CP6+CP7).
 * [availableExercises] powers the "add exercise" picker; it is populated
 * independently of [content] so it stays available even before a session is
 * started. [availablePlans] powers the start-workout plan picker (Milestone
 * 8, CP6) - also independent of [content], for the same reason.
 */
data class ActiveWorkoutUiState(
    val content: ActiveWorkoutContent = ActiveWorkoutContent.Loading,
    val availableExercises: List<ExercisePickerItem> = emptyList(),
    val availablePlans: List<TrainingPlanPickerItem> = emptyList(),
    val errorMessage: ActiveWorkoutErrorReason? = null,
)

/** One selectable entry in the start-workout plan picker (Milestone 8, CP6). */
data class TrainingPlanPickerItem(
    val versionId: TrainingPlanVersionId,
    val planName: String,
)

/** A read-only summary of recovery/futsal context for the active workout screen, per [com.repflow.app.application.recovery.WorkoutDayContext]. */
data class WorkoutDayContextUi(
    val heavyLegs: Int?,
    val legDoms: Int?,
    val futsalLoad: Double?,
)

data class ExercisePickerItem(
    val id: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val recommendation: ProgressionRecommendationUi? = null,
)

sealed interface ActiveWorkoutContent {
    data object Loading : ActiveWorkoutContent

    data object NoActiveSession : ActiveWorkoutContent

    data class Active(
        val sessionId: WorkoutSessionId,
        val startedAt: Instant,
        val exercises: List<ActiveExerciseUi>,
        val restTimer: RestTimerUi? = null,
        /** The plan the session was started from, for the board's title; `null` for an ad-hoc session (`Untitled workout`) or a label that no longer resolves. Remediation-1 CP7. */
        val planName: String? = null,
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
    /** The resolved plan target this exercise was seeded from, `null` for an ad-hoc exercise (Milestone 8, implementation-review finding #2). */
    val plannedTarget: PlannedTargetUi? = null,
    /**
     * The library exercise this workout exercise records, so focus mode can show
     * its progression suggestion and route `Why ›` to it (remediation-1 CP8).
     */
    val exerciseId: ExerciseId? = null,
    /**
     * [com.repflow.app.domain.exercise.Exercise.defaultLoadIncrement] in kg - the
     * weight stepper's step (`6b`: "Step size comes from the exercise's load
     * increment"); `null` when the exercise has none (remediation-1 CP8).
     */
    val defaultLoadIncrement: BigDecimal? = null,
    /** [com.repflow.app.domain.exercise.Exercise.instructions], shown as focus mode's technique notes; `null` when there are none (remediation-1 CP8). */
    val instructions: String? = null,
)

/**
 * A resolved [com.repflow.app.domain.trainingplan.PlannedExercise]'s target
 * shape for the active-workout screen: how many warm-up/working sets are
 * planned, the target rep or duration range (exactly one of [repRange]/
 * [durationRangeSeconds] is non-null, mirroring
 * [com.repflow.app.domain.trainingplan.PlannedExerciseTarget]), and the
 * planned rest duration.
 */
data class PlannedTargetUi(
    val targetWarmupSets: Int?,
    val targetWorkingSets: Int,
    val repRange: IntRange? = null,
    val durationRangeSeconds: LongRange? = null,
    val restSeconds: Int? = null,
)

data class ActiveSetUi(
    val id: WorkoutSetId,
    val setNumber: Int,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val rpe: Double? = null,
    val isWarmup: Boolean = false,
    val pain: Int? = null,
    val techniqueQuality: Int? = null,
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
