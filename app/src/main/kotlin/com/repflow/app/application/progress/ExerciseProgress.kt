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
import java.time.ZoneId

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
 * One metric's points, oldest first. The range pills narrow it with [within];
 * everything else (a trend, the delta, the best) reads the points it holds, so
 * the same functions serve the whole history and a `3m` or `6m` slice of it.
 *
 * @property points every valid session that produced a value, oldest first.
 */
data class ProgressSeries(
    val points: List<ProgressPoint>,
) {
    /** A trend needs two points; fewer is the offered metric's empty state (plan item 7). */
    val hasTrend: Boolean get() = points.size >= MIN_TREND_POINTS

    val latest: ProgressPoint? get() = points.lastOrNull()

    /** The first point, which the delta is measured from. */
    val first: ProgressPoint? get() = points.firstOrNull()

    /** Latest minus the first value; `null` without a trend. */
    val delta: BigDecimal? get() = if (hasTrend) points.last().value.subtract(points.first().value) else null

    /**
     * [delta] as a whole percent of the first value, half-up; `null` without a
     * trend and when the first value is 0 (a percent of nothing).
     */
    val deltaPercent: BigDecimal?
        get() {
            val change = delta ?: return null
            val start = points.first().value
            if (start.signum() == 0) return null
            return change.multiply(PERCENT).divide(start, 0, RoundingMode.HALF_UP)
        }

    /** The highest value (`Best: 82.5 kg`). */
    val best: BigDecimal? get() = points.maxOfOrNull { it.value }

    /** The points inside [range] as of [now]: later than its exclusive bound, or all of them for `All`. */
    fun within(
        range: ProgressRange,
        now: Instant,
        zone: ZoneId,
    ): ProgressSeries {
        val bound = range.startsAfter(now, zone) ?: return this
        return ProgressSeries(points.filter { it.startedAt > bound })
    }

    companion object {
        const val MIN_TREND_POINTS = 2
        private val PERCENT = BigDecimal(100)
    }
}

/**
 * One valid session's working sets of one exercise (every occurrence of it in
 * that session together); the raw material of the
 * Q6 derivations in `ExerciseProgressDetail.kt`. Sessions with no working set
 * of the exercise are not listed.
 */
data class SessionPerformance(
    val sessionId: WorkoutSessionId,
    val startedAt: Instant,
    val workingSets: List<WorkoutSet>,
)

/**
 * One exercise's progress, as the Progress tab's picker and card read it.
 *
 * @property name the name recorded with the most recent session that trained
 *   it - the snapshot keeps a renamed or archived exercise's history readable.
 * @property trackingType that session's tracking type; an earlier session
 *   recorded under another type contributes no point.
 * @property lastTrainedAt when the most recent valid session that trained it
 *   started - the picker is ordered by it, newest first. An occurrence with no
 *   set at all is not training (B6).
 * @property series one entry per [offeredMetrics], possibly with no points.
 * @property performances the working sets behind [series], one entry per
 *   session, oldest first; the tiles, frequency and records derive from them.
 */
data class ExerciseProgress(
    val exerciseId: ExerciseId,
    val name: String,
    val trackingType: ExerciseTrackingType,
    val lastTrainedAt: Instant,
    val series: Map<ProgressMetric, ProgressSeries>,
    val performances: List<SessionPerformance> = emptyList(),
) {
    val offeredMetrics: List<ProgressMetric> get() = ProgressMetric.offeredFor(trackingType)

    /**
     * `Est. 1RM` has no point although the exercise has loaded working sets:
     * every one is longer than [EstimatedOneRepMax.MAX_REPS] reps, so no
     * session can ever give it a value however many are logged. That is not
     * "not enough sessions", and the card says what is actually missing.
     */
    val estimateNeedsShorterSet: Boolean
        get() =
            ProgressMetric.ESTIMATED_ONE_REP_MAX in series &&
                series[ProgressMetric.ESTIMATED_ONE_REP_MAX]?.points.isNullOrEmpty() &&
                !series[ProgressMetric.VOLUME]?.points.isNullOrEmpty()
}

/**
 * Derives every trained exercise's progress from [sessions].
 *
 * **Only valid sessions count** (plan item 4): anything not completed, and any
 * invalidated session, is dropped here as well as by the query that feeds it,
 * so invalidating a workout removes its points. **One "trained" rule (B6):**
 * for listing, ordering, [ExerciseProgress.lastTrainedAt], `name` and
 * `trackingType` an occurrence counts when it has any set, so an exercise that
 * was added to a workout but never logged is not "most recent" and, never
 * having been trained, is not listed. For the series, tiles and records only
 * **working** sets count: warm-ups never contribute. A session contributes a
 * point to a metric only when it has a value for it - a weight-and-reps
 * session whose working sets carry no load gives no point to any of the three.
 * An exercise added twice to one session is one point, over all its sets.
 */
fun exerciseProgressOf(sessions: List<WorkoutSession>): List<ExerciseProgress> {
    val valid =
        sessions
            .filter { it.status == WorkoutSessionStatus.COMPLETED && !it.isInvalidated }
            .sortedBy { it.startedAt }
    val occurrences =
        valid.flatMap { session -> session.exercises.filter { it.sets.isNotEmpty() }.map { session to it } }
    return occurrences
        .groupBy { (_, exercise) -> exercise.exerciseId }
        .map { (exerciseId, chronological) ->
            val (lastSession, lastExercise) = chronological.last()
            val trackingType = lastExercise.trackingType
            val performances =
                chronological
                    .filter { (_, exercise) -> exercise.trackingType == trackingType }
                    .groupBy { (session, _) -> session.id }
                    .values
                    .map { inSession ->
                        SessionPerformance(
                            sessionId = inSession.first().first.id,
                            startedAt = inSession.first().first.startedAt,
                            workingSets = inSession.flatMap { (_, exercise) -> exercise.sets.filterNot { it.isWarmup } },
                        )
                    }
            ExerciseProgress(
                exerciseId = exerciseId,
                name = lastExercise.exerciseNameSnapshot,
                trackingType = trackingType,
                lastTrainedAt = lastSession.startedAt,
                series =
                    ProgressMetric.offeredFor(trackingType).associateWith { metric ->
                        ProgressSeries(
                            performances.mapNotNull { performance ->
                                metricValue(metric, trackingType, performance.workingSets)
                                    ?.let { ProgressPoint(performance.sessionId, performance.startedAt, it) }
                            },
                        )
                    },
                performances = performances.filter { it.workingSets.isNotEmpty() },
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
