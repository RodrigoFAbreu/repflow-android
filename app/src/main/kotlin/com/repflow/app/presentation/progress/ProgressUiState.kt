package com.repflow.app.presentation.progress

import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.domain.exercise.ExerciseId

/**
 * The Progress tab (remediation-1 CP15, `4a`): every trained exercise, the
 * one being charted, and the metric chosen for it.
 *
 * @property selectedExerciseId the chip the user picked; `null` (or an exercise
 *   that has since left the history) falls back to the most recently trained.
 * @property selectedMetric the segment the user picked. It is kept when they
 *   move to an exercise that does not offer it, which then charts its first
 *   offered metric (`Top set`), so moving back restores their choice.
 */
data class ProgressUiState(
    val isLoading: Boolean = true,
    val exercises: List<ExerciseProgress> = emptyList(),
    val selectedExerciseId: ExerciseId? = null,
    val selectedMetric: ProgressMetric = ProgressMetric.TOP_SET,
) {
    val exercise: ExerciseProgress? =
        exercises.firstOrNull { it.exerciseId == selectedExerciseId } ?: exercises.firstOrNull()

    /** The metric actually charted: the user's choice when [exercise] offers it, else its first offered one. */
    val metric: ProgressMetric =
        exercise?.offeredMetrics?.let { offered -> if (selectedMetric in offered) selectedMetric else offered.first() }
            ?: selectedMetric

    /**
     * The card still draws the parent's last-12-sessions bars until CP3 replaces
     * it with the date-ranged line chart (Q7); the read model itself no longer caps.
     */
    val series: ProgressSeries? = exercise?.series?.get(metric)?.let { ProgressSeries(it.points.takeLast(CARD_WINDOW_SIZE)) }

    /** `D22`: a reps-only or timed exercise offers `Top set` alone and says why. */
    val showsLoadMetricsUnavailable: Boolean = exercise?.trackingType?.supportsLoad == false
}

private const val CARD_WINDOW_SIZE = 12
