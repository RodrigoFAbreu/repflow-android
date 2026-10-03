package com.repflow.app.application.progress

import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.EstimatedOneRepMax
import com.repflow.app.domain.workout.WorkoutSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/*
 * The Progress tab's per-exercise derivations behind `5b`'s tiles, `Training
 * frequency` and `Records` (remediation-1-remediation-1 CP2, decision Q6).
 * Pure functions of an exercise's [SessionPerformance]s, a range and `now`;
 * every one is for the chosen exercise and counts working sets only.
 */

/** `Sessions` tile: sessions of the exercise with at least one working set of it, inside [range]. */
fun ExerciseProgress.sessionCount(
    range: ProgressRange,
    now: Instant,
    zone: ZoneId,
): Int = performancesIn(range, now, zone).size

/**
 * `Avg RPE` tile: the mean of the recorded RPE over the working sets inside
 * [range], to one decimal place, half-up; `null` (shown as `—`) when none has one.
 */
fun ExerciseProgress.averageRpe(
    range: ProgressRange,
    now: Instant,
    zone: ZoneId,
): BigDecimal? {
    val recorded = performancesIn(range, now, zone).flatMap { it.workingSets }.mapNotNull { it.rpe }
    if (recorded.isEmpty()) return null
    return recorded
        .map(BigDecimal::valueOf)
        .fold(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal(recorded.size), RPE_SCALE, RoundingMode.HALF_UP)
}

private fun ExerciseProgress.performancesIn(
    range: ProgressRange,
    now: Instant,
    zone: ZoneId,
): List<SessionPerformance> {
    val bound = range.startsAfter(now, zone) ?: return performances
    return performances.filter { it.startedAt > bound }
}

/** One calendar week (Monday start) and how many sessions of the exercise fell in it. */
data class WeekCount(
    val weekStart: LocalDate,
    val sessions: Int,
)

/**
 * `Training frequency`: [weeks] is the last [FREQUENCY_WEEKS] calendar weeks,
 * oldest first, the last being the week of `now`; [averagePerWeek] is their
 * total over [FREQUENCY_WEEKS] (empty weeks count), to one decimal place.
 */
data class WeeklyFrequency(
    val weeks: List<WeekCount>,
    val averagePerWeek: BigDecimal,
)

const val FREQUENCY_WEEKS = 8

/** Ignores the range pills (Q6). Weeks are local-calendar weeks, so a DST change cannot move a session. */
fun ExerciseProgress.weeklyFrequency(
    now: Instant,
    zone: ZoneId,
): WeeklyFrequency {
    val thisWeek = mondayOf(now.atZone(zone).toLocalDate())
    val firstWeek = thisWeek.minusWeeks((FREQUENCY_WEEKS - 1).toLong())
    val perWeek = performances.groupingBy { mondayOf(it.startedAt.atZone(zone).toLocalDate()) }.eachCount()
    val weeks =
        (0 until FREQUENCY_WEEKS).map { offset ->
            val start = firstWeek.plusWeeks(offset.toLong())
            WeekCount(start, perWeek[start] ?: 0)
        }
    val average =
        BigDecimal(weeks.sumOf { it.sessions }).divide(BigDecimal(FREQUENCY_WEEKS), AVERAGE_SCALE, RoundingMode.HALF_UP)
    return WeeklyFrequency(weeks, average)
}

private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** `Heaviest set - <load> x <reps>` and its date. */
data class HeaviestSet(
    val load: BigDecimal,
    val reps: Int,
    val on: Instant,
)

/** `Best est. 1RM - <value>` (Brzycki, 12-rep ceiling, whole kilograms: the chart's own value) and its date. */
data class BestEstimate(
    val value: BigDecimal,
    val on: Instant,
)

/** An exercise's all-time records (Q6, ignoring the range), each dated. */
sealed interface ExerciseRecords {
    /**
     * Weight-and-reps: the heaviest working load with the most reps at it
     * (ties: latest date); [bestEstimatedOneRepMax] is absent when no working
     * set is of 12 reps or fewer (the row then shows `-`).
     */
    data class Loaded(
        val heaviestSet: HeaviestSet,
        val bestEstimatedOneRepMax: BestEstimate?,
    ) : ExerciseRecords

    /** Reps-only: `Most reps in a set` (ties: latest date). */
    data class MostReps(
        val reps: Int,
        val on: Instant,
    ) : ExerciseRecords

    /** Timed: `Longest hold` in seconds (ties: latest date). */
    data class LongestHold(
        val seconds: Int,
        val on: Instant,
    ) : ExerciseRecords
}

/** The exercise's records, or `null` when no working set carries the value a record needs. */
fun ExerciseProgress.records(): ExerciseRecords? =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> {
            loadedRecords()
        }

        ExerciseTrackingType.REPS_ONLY -> {
            latestMax { it.reps }?.let { (reps, on) -> ExerciseRecords.MostReps(reps, on) }
        }

        ExerciseTrackingType.DURATION -> {
            latestMax { it.durationSeconds }?.let { (seconds, on) -> ExerciseRecords.LongestHold(seconds, on) }
        }
    }

private fun ExerciseProgress.loadedRecords(): ExerciseRecords.Loaded? {
    val loaded =
        performances.flatMap { performance ->
            performance.workingSets.mapNotNull { set ->
                set.load?.let { LoadedSet(BigDecimal.valueOf(it), set.reps, performance.startedAt) }
            }
        }
    val heaviest =
        loaded.maxWithOrNull(compareBy<LoadedSet> { it.load }.thenBy { it.reps ?: 0 }.thenBy { it.on }) ?: return null
    val estimate =
        loaded
            .mapNotNull { entry -> entry.estimate()?.let { BestEstimate(it, entry.on) } }
            .maxWithOrNull(compareBy<BestEstimate> { it.value }.thenBy { it.on })
    return ExerciseRecords.Loaded(HeaviestSet(heaviest.load, heaviest.reps ?: 0, heaviest.on), estimate)
}

private class LoadedSet(
    val load: BigDecimal,
    val reps: Int?,
    val on: Instant,
) {
    /** Whole-kilogram Brzycki estimate, as `estimatedOneRepMax` rounds it for the chart. */
    fun estimate(): BigDecimal? = reps?.let { EstimatedOneRepMax.brzycki(load, it) }?.setScale(0, RoundingMode.HALF_UP)
}

private fun ExerciseProgress.latestMax(value: (WorkoutSet) -> Int?): Pair<Int, Instant>? =
    performances
        .flatMap { performance -> performance.workingSets.mapNotNull { set -> value(set)?.let { it to performance.startedAt } } }
        .maxWithOrNull(compareBy<Pair<Int, Instant>> { it.first }.thenBy { it.second })

private const val RPE_SCALE = 1
private const val AVERAGE_SCALE = 1
