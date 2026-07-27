package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.RestTimer
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Default rest duration used when no per-exercise configuration exists yet (see milestone-4-reference.md). */
const val DEFAULT_REST_TIMER_SECONDS: Int = 90

class StartRestTimer
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        /** Starts (or replaces) the active session's rest timer, immediately persisted. */
        @Suppress("ReturnCount")
        suspend operator fun invoke(
            sessionId: WorkoutSessionId,
            durationSeconds: Int = DEFAULT_REST_TIMER_SECONDS,
        ): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val now = clock.now()
            val updated =
                session.withStartedRestTimer(RestTimer.start(durationSeconds, now)).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(updated)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
