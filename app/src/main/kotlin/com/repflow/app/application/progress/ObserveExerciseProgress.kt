package com.repflow.app.application.progress

import com.repflow.app.application.workout.WorkoutRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Emits every trained exercise's [ExerciseProgress], most recently trained
 * first, whenever the workout history changes (remediation-1 CP15).
 *
 * Reads valid sessions only: invalidated ones are excluded by the query and
 * again by [exerciseProgressOf], so invalidating a workout in History removes
 * its points from the chart - the design's own note on the Progress tab.
 */
class ObserveExerciseProgress
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        operator fun invoke(): Flow<List<ExerciseProgress>> =
            repository
                .observeCompletedSessions(includeInvalidated = false)
                .map(::exerciseProgressOf)
                .distinctUntilChanged()
    }
