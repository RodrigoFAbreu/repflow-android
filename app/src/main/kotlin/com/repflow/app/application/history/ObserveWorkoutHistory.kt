package com.repflow.app.application.history

import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Emits every completed workout session, most recently ended first, for the
 * history screen - including invalidated ones (Milestone 8, CP11/CP13), so
 * the screen's own show/hide filter can toggle their visibility without
 * resubscribing to a different query.
 */
class ObserveWorkoutHistory
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        operator fun invoke(): Flow<List<WorkoutSession>> = repository.observeCompletedSessions(includeInvalidated = true)
    }
