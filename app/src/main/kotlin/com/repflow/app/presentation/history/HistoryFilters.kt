package com.repflow.app.presentation.history

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanId
import java.time.LocalDate

/**
 * The user-selected History filter/sort criteria (Milestone 8, CP13). All
 * filtering happens client-side over the already-loaded session list (see
 * [HistoryUiState.visibleSessions]) - History's dataset is a single user's
 * own completed workouts, not an unbounded table, so there is no need for
 * per-filter Room queries the way `ExerciseListViewModel`'s search needs one.
 */
data class HistoryFilters(
    val exerciseId: ExerciseId? = null,
    val plan: HistoryPlanFilter = HistoryPlanFilter.Any,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val showInvalidated: Boolean = false,
    val sortOrder: HistorySortOrder = HistorySortOrder.NEWEST_FIRST,
)

/**
 * A session's `trainingPlanVersionId` is fixed at the exact version it was
 * started from (never re-pointed at a later revision - see
 * [com.repflow.app.domain.workout.WorkoutSession]'s KDoc), so filtering by
 * training plan means "any version of this plan", not one specific version;
 * [Plan] is therefore keyed by [TrainingPlanId], not a version id.
 */
sealed interface HistoryPlanFilter {
    data object Any : HistoryPlanFilter

    data object AdHocOnly : HistoryPlanFilter

    data class Plan(
        val planId: TrainingPlanId,
        val planName: String,
    ) : HistoryPlanFilter
}

enum class HistorySortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
}

/** One entry in the exercise-filter picker. */
data class HistoryExerciseFilterOption(
    val id: ExerciseId,
    val name: String,
)
