package com.repflow.app.presentation.history

import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.time.ZoneId

/*
 * History's plain values (remediation-1 CP12, `3a` and `3b`), kept out of the
 * composables so a JVM test can pin them: the counts and volume a row and the
 * detail's tiles show, the month sections the list is cut into, and what one
 * logged set reads as.
 */

/** Working sets across the session - warm-ups are counted apart (`3b`'s `2 warm-up sets`). */
internal fun workingSetCount(session: WorkoutSession): Int = session.exercises.sumOf { exercise -> exercise.sets.count { !it.isWarmup } }

/**
 * The session's volume in whole kilograms: load × reps summed over every
 * working set that records both. `null` when no working set carries a load -
 * a bodyweight or timed session has no volume to show, rather than `0 kg`.
 */
internal fun volumeKg(session: WorkoutSession): BigDecimal? {
    val products =
        session.exercises
            .flatMap { it.sets }
            .filterNot { it.isWarmup }
            .mapNotNull { set ->
                val load = set.load ?: return@mapNotNull null
                val reps = set.reps ?: return@mapNotNull null
                BigDecimal.valueOf(load).multiply(BigDecimal(reps))
            }
    if (products.isEmpty()) return null
    return products.fold(BigDecimal.ZERO, BigDecimal::add).setScale(0, RoundingMode.HALF_UP)
}

/** One month's run of sessions under its `3a` section label (`August 2026`). */
internal data class HistoryMonthSection(
    val month: YearMonth,
    val sessions: List<WorkoutSession>,
)

/**
 * Cuts [sessions] into consecutive runs by the month each started in, keeping
 * their order - so the sections follow whichever sort the list is in, and a
 * month never appears twice.
 */
internal fun monthSections(
    sessions: List<WorkoutSession>,
    zone: ZoneId,
): List<HistoryMonthSection> =
    sessions.fold(emptyList()) { sections, session ->
        val month = YearMonth.from(session.startedAt.atZone(zone))
        val last = sections.lastOrNull()
        if (last != null && last.month == month) {
            sections.dropLast(1) + last.copy(sessions = last.sessions + session)
        } else {
            sections + HistoryMonthSection(month, listOf(session))
        }
    }

/**
 * What one logged set reads as, chosen by the exercise's tracking type - never
 * a load-and-reps template a timed set cannot fill, which is the parent
 * milestone's `Set 0:  kg x ` defect this replaces.
 */
internal sealed interface HistorySetValue {
    /** `70 kg × 10`, or `70 kg` if the reps were not recorded. */
    data class Load(
        val kg: BigDecimal,
        val reps: Int?,
    ) : HistorySetValue

    /** `12 reps`: a reps-only set, or a weighted one logged without a load. */
    data class Reps(
        val reps: Int,
    ) : HistorySetValue

    /** `45 s`. */
    data class Seconds(
        val seconds: Int,
    ) : HistorySetValue

    /** Nothing the tracking type measures was recorded. */
    data object Empty : HistorySetValue
}

internal fun historySetValueOf(
    trackingType: ExerciseTrackingType,
    set: WorkoutSet,
): HistorySetValue {
    val load = set.load
    val reps = set.reps
    val seconds = set.durationSeconds
    return when {
        trackingType == ExerciseTrackingType.DURATION -> if (seconds != null) HistorySetValue.Seconds(seconds) else HistorySetValue.Empty
        load != null -> HistorySetValue.Load(BigDecimal.valueOf(load), reps)
        reps != null -> HistorySetValue.Reps(reps)
        else -> HistorySetValue.Empty
    }
}

/**
 * `3b`'s set numbers: working sets count 1, 2, 3 in the order they were
 * logged; a warm-up has no number (it is marked `warm-up` instead), so the
 * numbers match the `Sets` tile.
 */
internal fun workingSetNumbers(sets: List<WorkoutSet>): List<Int?> {
    var next = 0
    return sets.map { set -> if (set.isWarmup) null else ++next }
}

/** `8`, `8.5` - an RPE without a trailing `.0`. */
internal fun rpeText(rpe: Double): String = BigDecimal.valueOf(rpe).stripTrailingZeros().toPlainString()
