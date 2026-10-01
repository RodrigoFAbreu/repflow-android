package com.repflow.app.presentation.home

import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.Instant
import java.time.LocalDate

/**
 * Everything Home draws (remediation-1 CP5), top to bottom: the header's
 * [date], the resume card ([activeWorkout]), the start card ([start]) and its
 * sheet ([startOptions]), the recovery card ([readiness]) and the
 * `Last workout` card ([lastWorkout]).
 *
 * [openWorkout] is a one-shot request to show the workout surface, raised
 * after a start succeeds and cleared by the route once it has navigated.
 */
data class HomeUiState(
    val date: LocalDate,
    val activeWorkout: HomeActiveWorkout? = null,
    val start: HomeStartCard = HomeStartCard.Loading,
    val startOptions: List<HomePlanOption> = emptyList(),
    val readiness: HomeReadiness = HomeReadiness.Loading,
    val lastWorkout: HomeLastWorkout = HomeLastWorkout.Loading,
    val error: HomeErrorReason? = null,
    val openWorkout: Boolean = false,
)

/**
 * The running session the resume card describes.
 *
 * @property planName the plan it was started from, or `null` for a workout
 *   started without one (drawn as `Untitled workout`, the design's default).
 */
data class HomeActiveWorkout(
    val sessionId: WorkoutSessionId,
    val planName: String?,
    val startedAt: Instant,
    val setsLogged: Int,
)

/** One startable plan: the start card's plan and each row of the start sheet. */
data class HomePlanOption(
    val versionId: TrainingPlanVersionId,
    val planName: String,
    val exerciseCount: Int,
    val workingSetCount: Int,
)

sealed interface HomeStartCard {
    data object Loading : HomeStartCard

    /** The plan `Start workout` starts: the one last trained, else the first active plan. */
    data class Plan(
        val option: HomePlanOption,
    ) : HomeStartCard

    /** No active plan exists: `1d`'s first-run card (`Create a plan` / `Empty workout`). */
    data object NoPlan : HomeStartCard
}

sealed interface HomeReadiness {
    data object Loading : HomeReadiness

    /** No check-in for today: the design's `Nothing logged today` state. */
    data object NotLogged : HomeReadiness

    data class Logged(
        val score: ReadinessScore,
    ) : HomeReadiness

    /** Today's check-in could not be read: the card claims nothing either way. */
    data object Unavailable : HomeReadiness
}

sealed interface HomeLastWorkout {
    data object Loading : HomeLastWorkout

    /** No valid completed workout yet: the card is not drawn. */
    data object None : HomeLastWorkout

    data class Summary(
        val planName: String?,
        val startedAt: Instant,
        val endedAt: Instant,
        val loadIncreases: Int,
    ) : HomeLastWorkout

    /** `1d`'s "Couldn't load your history" card, with `Retry`. */
    data object Failed : HomeLastWorkout
}

enum class HomeErrorReason {
    ALREADY_ACTIVE,
    PLAN_NOT_FOUND,
    UNKNOWN,
}
