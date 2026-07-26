package com.repflow.app.application.history

import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits every completed workout session, most recently ended first, for the history screen. */
class ObserveWorkoutHistory
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        operator fun invoke(): Flow<List<WorkoutSession>> = repository.observeCompletedSessions()
    }
