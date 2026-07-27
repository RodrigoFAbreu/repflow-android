package com.repflow.app.application.workout

import com.repflow.app.application.common.Clock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.common.getOrElse
import com.repflow.app.domain.workout.WorkoutSessionId
import javax.inject.Inject

/** Whether [AdjustRestTimer] adds or removes time from the active rest timer. */
enum class RestTimerAdjustment { ADD, REMOVE }

class AdjustRestTimer
    @Inject
    constructor(
        private val repository: WorkoutRepository,
        private val clock: Clock,
    ) {
        /** Adds or removes [seconds] from the active session's running rest timer, immediately persisted. */
        @Suppress("ReturnCount")
        suspend operator fun invoke(
            sessionId: WorkoutSessionId,
            adjustment: RestTimerAdjustment,
            seconds: Long,
        ): DomainResult<Unit, WorkoutOperationError> {
            val session = repository.findById(sessionId) ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val timer = session.restTimer ?: return DomainResult.Failure(WorkoutOperationError.NotFound)
            val now = clock.now()
            val adjustedTimer =
                when (adjustment) {
                    RestTimerAdjustment.ADD -> timer.withAddedSeconds(seconds)
                    RestTimerAdjustment.REMOVE -> timer.withRemovedSeconds(seconds, now)
                }
            val updated =
                session.withAdjustedRestTimer(adjustedTimer).getOrElse { error ->
                    return DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error)))
                }
            return when (val result = repository.update(updated)) {
                is DomainResult.Success -> DomainResult.Success(Unit)
                is DomainResult.Failure -> DomainResult.Failure(result.error.toOperationError())
            }
        }
    }
