package com.repflow.app.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.domain.exercise.ExerciseTrackingType

/*
 * The words the Progress tab puts on its metrics, ranges and units
 * (`5b`), kept in one place so the card, the readout and the spoken chart
 * summary name things the same way.
 */

/** `Heaviest working set` for a loaded top set; the other two metrics and the other tracking types name themselves. */
@Composable
internal fun metricCaption(
    trackingType: ExerciseTrackingType,
    metric: ProgressMetric,
): String =
    when (metric) {
        ProgressMetric.TOP_SET -> {
            when (trackingType) {
                ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.progress_caption_top_set)
                ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.progress_caption_most_reps)
                ExerciseTrackingType.DURATION -> stringResource(R.string.progress_caption_longest_set)
            }
        }

        ProgressMetric.ESTIMATED_ONE_REP_MAX -> {
            stringResource(R.string.progress_caption_estimated_one_rep_max)
        }

        ProgressMetric.VOLUME -> {
            stringResource(R.string.progress_caption_volume)
        }
    }

@Composable
internal fun rangeLabel(range: ProgressRange): String =
    when (range) {
        ProgressRange.THREE_MONTHS -> stringResource(R.string.progress_range_3m)
        ProgressRange.SIX_MONTHS -> stringResource(R.string.progress_range_6m)
        ProgressRange.ALL -> stringResource(R.string.progress_range_all)
    }

@Composable
internal fun rangeSpoken(range: ProgressRange): String =
    when (range) {
        ProgressRange.THREE_MONTHS -> stringResource(R.string.progress_range_3m_spoken)
        ProgressRange.SIX_MONTHS -> stringResource(R.string.progress_range_6m_spoken)
        ProgressRange.ALL -> stringResource(R.string.progress_range_all_spoken)
    }
