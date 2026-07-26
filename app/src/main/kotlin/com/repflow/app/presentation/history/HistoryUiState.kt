package com.repflow.app.presentation.history

import com.repflow.app.application.trainingplan.TrainingPlanVersionLabel
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import java.time.ZoneId

/**
 * History screen state: every loaded completed session, the user's current
 * filter/sort selection, and an optional read-only detail selection.
 *
 * `messages` is a FIFO queue of [HistoryMessage]; each has a stable, unique,
 * monotonic id and is consumed via `onMessageShown(id)`, mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListUiState]'s
 * snackbar-hardening pattern (Milestone 8, CP2).
 */
data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<WorkoutSession> = emptyList(),
    val versionLabels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel> = emptyMap(),
    val filters: HistoryFilters = HistoryFilters(),
    val selectedSessionId: WorkoutSessionId? = null,
    val messages: List<HistoryMessage> = emptyList(),
) {
    /** Every exercise appearing in any loaded session, for the exercise-filter picker. */
    val availableExerciseOptions: List<HistoryExerciseFilterOption>
        get() =
            sessions
                .flatMap { it.exercises }
                .distinctBy { it.exerciseId }
                .map { HistoryExerciseFilterOption(it.exerciseId, it.exerciseNameSnapshot) }
                .sortedBy { it.name }

    /** Every distinct plan (by identity, not version) referenced by any loaded session, for the plan-filter picker. */
    val availablePlanOptions: List<HistoryPlanFilter.Plan>
        get() =
            sessions
                .mapNotNull { it.trainingPlanVersionId }
                .mapNotNull { versionLabels[it] }
                .distinctBy { it.planId }
                .map { HistoryPlanFilter.Plan(it.planId, it.planName) }
                .sortedBy { it.planName }

    /** [sessions] filtered by [filters] and sorted by [HistoryFilters.sortOrder] - what the list actually renders. */
    val visibleSessions: List<WorkoutSession>
        get() {
            val filtered = sessions.filter { session -> matches(session, filters, versionLabels) }
            return when (filters.sortOrder) {
                HistorySortOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.startedAt }
                HistorySortOrder.OLDEST_FIRST -> filtered.sortedBy { it.startedAt }
            }
        }

    val selectedSession: WorkoutSession?
        get() = sessions.find { it.id == selectedSessionId }
}

private fun matches(
    session: WorkoutSession,
    filters: HistoryFilters,
    versionLabels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel>,
): Boolean =
    matchesInvalidatedFilter(session, filters) &&
        matchesExerciseFilter(session, filters) &&
        matchesPlanFilter(session, filters, versionLabels) &&
        matchesDateRange(session, filters)

private fun matchesInvalidatedFilter(
    session: WorkoutSession,
    filters: HistoryFilters,
): Boolean = filters.showInvalidated || !session.isInvalidated

private fun matchesExerciseFilter(
    session: WorkoutSession,
    filters: HistoryFilters,
): Boolean {
    val exerciseId = filters.exerciseId ?: return true
    return session.exercises.any { it.exerciseId == exerciseId }
}

private fun matchesPlanFilter(
    session: WorkoutSession,
    filters: HistoryFilters,
    versionLabels: Map<TrainingPlanVersionId, TrainingPlanVersionLabel>,
): Boolean =
    when (val plan = filters.plan) {
        HistoryPlanFilter.Any -> true
        HistoryPlanFilter.AdHocOnly -> session.trainingPlanVersionId == null
        is HistoryPlanFilter.Plan -> session.trainingPlanVersionId?.let { versionLabels[it]?.planId } == plan.planId
    }

private fun matchesDateRange(
    session: WorkoutSession,
    filters: HistoryFilters,
): Boolean {
    val sessionDate = session.startedAt.atZone(ZoneId.systemDefault()).toLocalDate()
    val afterStart = filters.startDate?.let { sessionDate >= it } ?: true
    val beforeEnd = filters.endDate?.let { sessionDate <= it } ?: true
    return afterStart && beforeEnd
}

/** Snackbar-worthy events raised by the history screen. */
sealed interface HistoryMessage {
    val id: Long

    data class Invalidated(
        override val id: Long,
    ) : HistoryMessage

    data class OperationFailed(
        override val id: Long,
    ) : HistoryMessage
}
