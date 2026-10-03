package com.repflow.app.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.domain.exercise.ExerciseTrackingType

/** The value's unit (`kg`, `kg total`, `reps`, `s`) and the shorter one the delta and readout use. */
internal data class ProgressUnits(
    val long: String,
    val short: String,
)

@Composable
internal fun unitsOf(
    trackingType: ExerciseTrackingType,
    metric: ProgressMetric,
): ProgressUnits {
    val short =
        when (trackingType) {
            ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.progress_unit_kg)
            ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.progress_unit_reps)
            ExerciseTrackingType.DURATION -> stringResource(R.string.progress_unit_seconds)
        }
    val long = if (metric == ProgressMetric.VOLUME) stringResource(R.string.progress_unit_kg_total) else short
    return ProgressUnits(long = long, short = short)
}
