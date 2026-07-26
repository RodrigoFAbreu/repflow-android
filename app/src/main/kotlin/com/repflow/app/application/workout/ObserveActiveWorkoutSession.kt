package com.repflow.app.application.workout

import com.repflow.app.domain.workout.WorkoutSession
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Emits the current active workout session, or `null` when none is active - used to power a "resume workout" entry point. */
class ObserveActiveWorkoutSession
    @Inject
    constructor(
        private val repository: WorkoutRepository,
    ) {
        operator fun invoke(): Flow<WorkoutSession?> = repository.observeActiveSession()
    }
