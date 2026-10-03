package com.repflow.app.presentation.progress

import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ExerciseRecords
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.application.progress.WeeklyFrequency
import com.repflow.app.application.progress.averageRpe
import com.repflow.app.application.progress.records
import com.repflow.app.application.progress.sessionCount
import com.repflow.app.application.progress.weeklyFrequency
import com.repflow.app.domain.exercise.ExerciseId
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * The Progress tab (`5b`): every trained exercise, the one being charted, the
 * metric and the range chosen for it.
 *
 * @property selectedExerciseId the exercise the user picked; `null` (or one
 *   that has since left the history) falls back to the most recently trained.
 * @property selectedMetric the button the user picked. It is kept when they
 *   move to an exercise that does not offer it, which then charts its first
 *   offered metric (`Top set`), so moving back restores their choice.
 * @property range the range pill, `All` by default; kept across exercises.
 * @property now the instant the ranges and the weekly frequency are measured
 *   from, in [zone].
 */
data class ProgressUiState(
    val isLoading: Boolean = true,
    val exercises: List<ExerciseProgress> = emptyList(),
    val selectedExerciseId: ExerciseId? = null,
    val selectedMetric: ProgressMetric = ProgressMetric.TOP_SET,
    val range: ProgressRange = ProgressRange.ALL,
    val now: Instant = Instant.EPOCH,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val exercise: ExerciseProgress? =
        exercises.firstOrNull { it.exerciseId == selectedExerciseId } ?: exercises.firstOrNull()

    /** The metric actually charted: the user's choice when [exercise] offers it, else its first offered one. */
    val metric: ProgressMetric =
        exercise?.offeredMetrics?.let { offered -> if (selectedMetric in offered) selectedMetric else offered.first() }
            ?: selectedMetric

    /** The charted metric's points inside [range]: the chart, the headline value and the delta read this. */
    val series: ProgressSeries? = exercise?.series?.get(metric)?.within(range, now, zone)

    /** `D22`: a reps-only or timed exercise offers `Top set` alone and says why. */
    val showsLoadMetricsUnavailable: Boolean = exercise?.trackingType?.supportsLoad == false

    /** Tiles, frequency and records of [exercise] (Q6); `null` with no exercise. */
    val stats: ProgressStats? = exercise?.let { ProgressStats.of(it, range, now, zone) }
}

/**
 * `5b`'s lower sections, for the chosen exercise. [sessions] and [averageRpe]
 * follow the range pill; [frequency] and [records] ignore it (Q6).
 */
data class ProgressStats(
    val sessions: Int,
    val averageRpe: BigDecimal?,
    val frequency: WeeklyFrequency,
    val records: ExerciseRecords?,
) {
    companion object {
        fun of(
            exercise: ExerciseProgress,
            range: ProgressRange,
            now: Instant,
            zone: ZoneId,
        ) = ProgressStats(
            sessions = exercise.sessionCount(range, now, zone),
            averageRpe = exercise.averageRpe(range, now, zone),
            frequency = exercise.weeklyFrequency(now, zone),
            records = exercise.records(),
        )
    }
}
