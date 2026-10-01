package com.repflow.app.application.progress

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.EstimatedOneRepMax
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/*
 * The Progress tab's read model (remediation-1 CP15, plan items 3, 4 and 6):
 * one exercise, one metric, one point per valid session - derived from the
 * workout history on every change, never stored.
 */

/** The three things the Progress tab can chart (`4a`'s `Top set` / `Est. 1RM` / `Volume`). */
enum class ProgressMetric {
    /** The heaviest working load; for a reps-only exercise the most reps, for a timed one the longest set. */
    TOP_SET,

    /**
     * The best per-set [EstimatedOneRepMax] over loaded working sets of 1 to
     * [EstimatedOneRepMax.MAX_REPS] reps, in whole kilograms.
     */
    ESTIMATED_ONE_REP_MAX,

    /** Σ load × reps over loaded working sets, in whole kilograms. */
    VOLUME,
    ;

    companion object {
        /**
         * What an exercise of [trackingType] can chart (plan item 6, `D22`):
         * all three for weight-and-reps; `Top set` alone for reps-only and
         * duration exercises, which record no load to estimate or total.
         */
        fun offeredFor(trackingType: ExerciseTrackingType): List<ProgressMetric> =
            if (trackingType.supportsLoad) entries.toList() else listOf(TOP_SET)
    }
}

/** One valid session's value for one metric. */
data class ProgressPoint(
    val sessionId: WorkoutSessionId,
    val startedAt: Instant,
    val value: BigDecimal,
)

/**
 * One metric's points, oldest first, and the window the chart draws.
 *
 * @property points every valid session that produced a value, oldest first.
 */
data class ProgressSeries(
    val points: List<ProgressPoint>,
) {
    /** The last [WINDOW_SIZE] points - `4a`'s "12-session window". */
    val window: List<ProgressPoint> = points.takeLast(WINDOW_SIZE)

    /** A trend needs two points; fewer is the offered metric's empty state (plan item 7). */
    val hasTrend: Boolean = window.size >= MIN_TREND_POINTS

    val latest: ProgressPoint? = window.lastOrNull()

    /** The window's first point, which the delta is measured from (`since 5 May`). */
    val windowStart: ProgressPoint? = window.firstOrNull()

    /** Latest minus the window's first value; `null` without a trend. */
    val delta: BigDecimal? = if (hasTrend) window.last().value.subtract(window.first().value) else null

    /** The window's highest value (`Best: 82.5 kg`). */
    val best: BigDecimal? = window.maxOfOrNull { it.value }

    companion object {
        const val WINDOW_SIZE = 12
        const val MIN_TREND_POINTS = 2
    }
}

/**
 * One exercise's progress, as the Progress tab's chip and card read it.
 *
 * @property name the name recorded with the most recent session that included
 *   it - the snapshot keeps a renamed or archived exercise's history readable.
 * @property trackingType the most recent session's tracking type; an earlier
 *   session recorded under another type contributes no point.
 * @property lastTrainedAt when the most recent valid session that included it
 *   started - the chips are ordered by it, newest first.
 * @property series one entry per [offeredMetrics], possibly with no points.
 */
data class ExerciseProgress(
    val exerciseId: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val lastTrainedAt: Instant,
    val series: Map<ProgressMetric, ProgressSeries>,
) {
    val offeredMetrics: List<ProgressMetric> get() = ProgressMetric.offeredFor(trackingType)
}

/**
 * Derives every trained exercise's progress from [sessions].
 *
 * **Only valid sessions count** (plan item 4): anything not completed, and any
 * invalidated session, is dropped here as well as by the query that feeds it,
 * so invalidating a workout removes its points. **Only working sets count**:
 * warm-ups never contribute. A session contributes a point to a metric only
 * when it has a value for it - a weight-and-reps session whose working sets
 * carry no load gives no point to any of the three. An exercise added twice to
 * one session is one point, over all its sets.
 */
fun exerciseProgressOf(sessions: List<WorkoutSession>): List<ExerciseProgress> {
    val valid =
        sessions
            .filter { it.status == WorkoutSessionStatus.COMPLETED && !it.isInvalidated }
            .sortedBy { it.startedAt }
    val occurrences = valid.flatMap { session -> session.exercises.map { session to it } }
    return occurrences
        .groupBy { (_, exercise) -> exercise.exerciseId }
        .map { (exerciseId, chronological) ->
            val (lastSession, lastExercise) = chronological.last()
            val trackingType = lastExercise.trackingType
            val workingSetsBySession =
                chronological
                    .filter { (_, exercise) -> exercise.trackingType == trackingType }
                    .groupBy { (session, _) -> session.id }
                    .values
                    .map { inSession ->
                        inSession.first().first to
                            inSession.flatMap { (_, exercise) -> exercise.sets.filterNot { it.isWarmup } }
                    }
            ExerciseProgress(
                exerciseId = exerciseId,
                name = lastExercise.exerciseNameSnapshot,
                trackingType = trackingType,
                lastTrainedAt = lastSession.startedAt,
                series =
                    ProgressMetric.offeredFor(trackingType).associateWith { metric ->
                        ProgressSeries(
                            workingSetsBySession.mapNotNull { (session, sets) ->
                                metricValue(metric, trackingType, sets)?.let { ProgressPoint(session.id, session.startedAt, it) }
                            },
                        )
                    },
            )
        }.sortedByDescending { it.lastTrainedAt }
}

private fun metricValue(
    metric: ProgressMetric,
    trackingType: ExerciseTrackingType,
    workingSets: List<WorkoutSet>,
): BigDecimal? =
    when (metric) {
        ProgressMetric.TOP_SET -> topSet(trackingType, workingSets)
        ProgressMetric.ESTIMATED_ONE_REP_MAX -> estimatedOneRepMax(workingSets)
        ProgressMetric.VOLUME -> volume(workingSets)
    }

private fun topSet(
    trackingType: ExerciseTrackingType,
    workingSets: List<WorkoutSet>,
): BigDecimal? =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> workingSets.mapNotNull { it.load }.maxOrNull()?.let(BigDecimal::valueOf)
        ExerciseTrackingType.REPS_ONLY -> workingSets.mapNotNull { it.reps }.maxOrNull()?.let(::BigDecimal)
        ExerciseTrackingType.DURATION -> workingSets.mapNotNull { it.durationSeconds }.maxOrNull()?.let(::BigDecimal)
    }

private fun estimatedOneRepMax(workingSets: List<WorkoutSet>): BigDecimal? =
    workingSets
        .mapNotNull { set ->
            val load = set.load ?: return@mapNotNull null
            val reps = set.reps ?: return@mapNotNull null
            EstimatedOneRepMax.brzycki(BigDecimal.valueOf(load), reps)
        }.maxOrNull()
        ?.setScale(0, RoundingMode.HALF_UP)

private fun volume(workingSets: List<WorkoutSet>): BigDecimal? {
    val products =
        workingSets.mapNotNull { set ->
            val load = set.load ?: return@mapNotNull null
            val reps = set.reps ?: return@mapNotNull null
            BigDecimal.valueOf(load).multiply(BigDecimal(reps))
        }
    if (products.isEmpty()) return null
    return products.fold(BigDecimal.ZERO, BigDecimal::add).setScale(0, RoundingMode.HALF_UP)
}
