package com.repflow.app.presentation.workout

import com.repflow.app.application.settings.AppSettings
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.workout.LastPerformance
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
        /**
         * The app-wide `Default rest` in seconds (remediation-1-remediation-1 CP6,
         * Q9): the last link of the rest precedence, put here by the ViewModel
         * from the settings it observes so the displayed and the started rest
         * resolve from one value.
         */
        val appDefaultRestSeconds: Int = AppSettings.DEFAULT_REST_SECONDS,
        /** How focus mode shows RPE, pain and technique (`Extra set fields`, Q2). */
        val extraSetFields: ExtraSetFields = ExtraSetFields.DEFAULT,
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
    /** [com.repflow.app.domain.exercise.Exercise.defaultRestDuration] in seconds: the rest precedence's middle link (Q9); `null` when the exercise sets none. */
    val defaultRestSeconds: Int? = null,
    /**
     * What the user did on this exercise last time (Q8: the last working set of
     * the most recent valid session), for focus mode's `Last time:` line;
     * `null` for a never-done exercise (remediation-1-remediation-1 CP9).
     */
    val lastPerformance: LastPerformance? = null,
    /** What an untouched set entry starts from, computed by the ViewModel ([entrySeedOf]); `null` when there is nothing to seed. */
    val seed: SetEntrySeed? = null,
)

/**
 * The numbers an untouched set entry takes: this session's last logged set of
 * the exercise, else its [LastPerformance]. Only the fields the exercise's
 * tracking type records are set.
 */
data class SetEntrySeed(
    val load: BigDecimal? = null,
    val reps: BigDecimal? = null,
    val seconds: BigDecimal? = null,
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

/**
 * Where the finish sheet's confirm is (remediation-1 CP9). Kept apart from
 * [ActiveWorkoutUiState] so the observed-session stream is unchanged: the
 * session still goes `Active` → `NoActiveSession` on completion.
 *
 * Completing ends the active session, and the workout surface reads an ended
 * session as "nothing left to show here, go Home". [InFlight] is what tells it
 * this end is a finish, so it waits for [Finished] - which carries the id the
 * done screen opens with - instead.
 */
sealed interface WorkoutFinishState {
    data object Idle : WorkoutFinishState

    data object InFlight : WorkoutFinishState

    data class Finished(
        val sessionId: WorkoutSessionId,
    ) : WorkoutFinishState
}
