package com.repflow.app.application.history

import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What Home reads from the training history (remediation-1 CP5): the last
 * workout's summary and the plan version most recently trained.
 *
 * @property lastWorkout the most recently ended valid session, or `null` when
 *   none exists.
 * @property lastTrainedPlanVersionId the plan version of the most recently
 *   ended valid session that was started from a plan - the start card's "the
 *   plan you last trained" (plan CP5, the fallback for `4a`'s programmed day).
 */
data class RecentTraining(
    val lastWorkout: LastWorkoutSummary?,
    val lastTrainedPlanVersionId: TrainingPlanVersionId?,
)

/**
 * The last workout, as Home's `Last workout` card draws it.
 *
 * @property loadIncreases how many of its exercises were done at a heavier
 *   top working load than the same exercise's previous valid session - see
 *   [ObserveRecentTraining].
 */
data class LastWorkoutSummary(
    val session: WorkoutSession,
    val loadIncreases: Int,
)

/**
 * Derives [RecentTraining] from the completed-session history, reactively.
 *
 * **Only valid sessions count.** An invalidated session is left out here as it
 * is left out of progression, so it is neither "the last workout" nor the
 * comparison point for a load increase.
 *
 * **A load increase** is counted per exercise of the last workout: its top
 * working load (the heaviest non-warm-up set with a load) is above the top
 * working load of the most recent earlier valid session that has one for the
 * same exercise. An exercise with no earlier loaded session, or with no load
 * at all (reps-only, duration), is not an increase. Nothing is stored: the
 * count is recomputed from the sets themselves whenever the history changes.
 */
class ObserveRecentTraining
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        operator fun invoke(): Flow<RecentTraining> =
            repository
                .observeCompletedSessions(includeInvalidated = false)
                .map(::recentTrainingOf)
                .distinctUntilChanged()
    }

internal fun recentTrainingOf(sessions: List<WorkoutSession>): RecentTraining {
    val newestFirst = sessions.filter { it.invalidatedAt == null }.sortedByDescending { it.endedAt }
    val last = newestFirst.firstOrNull()
    return RecentTraining(
        lastWorkout = last?.let { LastWorkoutSummary(it, loadIncreasesOf(it, newestFirst.drop(1))) },
        lastTrainedPlanVersionId = newestFirst.firstNotNullOfOrNull { it.trainingPlanVersionId },
    )
}

private fun loadIncreasesOf(
    session: WorkoutSession,
    earlierNewestFirst: List<WorkoutSession>,
): Int =
    topWorkingLoads(session).count { (exerciseId, top) ->
        val previous = earlierNewestFirst.firstNotNullOfOrNull { topWorkingLoads(it)[exerciseId] }
        previous != null && top > previous
    }

private fun topWorkingLoads(session: WorkoutSession): Map<ExerciseId, Double> =
    session.exercises
        .groupBy(WorkoutExercise::exerciseId)
        .mapNotNull { (exerciseId, entries) ->
            entries
                .flatMap { it.sets }
                .filterNot { it.isWarmup }
                .mapNotNull { it.load }
                .maxOrNull()
                ?.let { exerciseId to it }
        }.toMap()
