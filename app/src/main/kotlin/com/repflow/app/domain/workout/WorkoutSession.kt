package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import java.time.Instant

/**
 * A concrete workout occurrence: [WorkoutSessionStatus.ACTIVE], then
 * terminally [WorkoutSessionStatus.COMPLETED] or
 * [WorkoutSessionStatus.ABANDONED].
 *
 * [trainingPlanVersionId] is fixed at [start] and never re-pointed at a
 * later version of the same plan - a plan revision must never change what
 * an already-started or completed session refers to, per the "training-plan
 * edits do not mutate completed workout history" invariant. It is null for
 * a session started without a plan.
 *
 * Only one [WorkoutSessionStatus.ACTIVE] session may exist at a time; that
 * invariant is enforced by the application layer (across all sessions), not
 * here (this type only knows about itself).
 */
@ConsistentCopyVisibility
@Suppress("TooManyFunctions")
data class WorkoutSession private constructor(
    val id: WorkoutSessionId,
    val trainingPlanVersionId: TrainingPlanVersionId?,
    val status: WorkoutSessionStatus,
    val startedAt: Instant,
    val endedAt: Instant?,
    val exercises: List<WorkoutExercise>,
    val restTimer: RestTimer?,
    val invalidatedAt: Instant?,
) {
    companion object {
        /** Starts a new [WorkoutSessionStatus.ACTIVE] session with no exercises yet. */
        fun start(
            id: WorkoutSessionId,
            trainingPlanVersionId: TrainingPlanVersionId?,
            startedAt: Instant,
        ): WorkoutSession =
            WorkoutSession(
                id = id,
                trainingPlanVersionId = trainingPlanVersionId,
                status = WorkoutSessionStatus.ACTIVE,
                startedAt = startedAt,
                endedAt = null,
                exercises = emptyList(),
                restTimer = null,
                invalidatedAt = null,
            )

        /** Rebuilds a session from persisted state, re-validating its invariants. */
        @Suppress("LongParameterList", "ReturnCount")
        fun reconstruct(
            id: WorkoutSessionId,
            trainingPlanVersionId: TrainingPlanVersionId?,
            status: WorkoutSessionStatus,
            startedAt: Instant,
            endedAt: Instant?,
            exercises: List<WorkoutExercise>,
            restTimer: RestTimer? = null,
            invalidatedAt: Instant? = null,
        ): DomainResult<WorkoutSession, WorkoutValidationError> {
            if (endedAt != null && endedAt < startedAt) {
                return DomainResult.Failure(WorkoutValidationError.EndedBeforeStarted)
            }
            val isTerminal = status != WorkoutSessionStatus.ACTIVE
            if (isTerminal && endedAt == null) {
                return DomainResult.Failure(WorkoutValidationError.EndedAtRequiredForTerminalStatus)
            }
            if (!isTerminal && endedAt != null) {
                return DomainResult.Failure(WorkoutValidationError.EndedAtNotAllowedForActiveSession)
            }
            if (invalidatedAt != null) {
                if (status != WorkoutSessionStatus.COMPLETED) {
                    return DomainResult.Failure(WorkoutValidationError.InvalidatedAtNotAllowedForIncompleteSession)
                }
                if (endedAt != null && invalidatedAt < endedAt) {
                    return DomainResult.Failure(WorkoutValidationError.InvalidatedBeforeEnded)
                }
            }
            val exerciseOrders = exercises.map { it.order }
            if (exerciseOrders.toSet().size != exerciseOrders.size) {
                return DomainResult.Failure(WorkoutValidationError.DuplicateExerciseOrder)
            }
            return DomainResult.Success(
                WorkoutSession(
                    id = id,
                    trainingPlanVersionId = trainingPlanVersionId,
                    status = status,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    exercises = exercises.sortedBy { it.order },
                    restTimer = if (isTerminal) null else restTimer,
                    invalidatedAt = invalidatedAt,
                ),
            )
        }
    }

    /** The next zero-based `order` value to use for a newly added exercise. */
    fun nextExerciseOrder(): Int = (exercises.maxOfOrNull { it.order } ?: -1) + 1

    /** Adds [exercise] to an active session, rejecting a duplicate `order`. */
    @Suppress("ReturnCount")
    fun withAddedExercise(exercise: WorkoutExercise): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        if (exercises.any { it.order == exercise.order }) {
            return DomainResult.Failure(WorkoutValidationError.DuplicateExerciseOrder)
        }
        return DomainResult.Success(copy(exercises = (exercises + exercise).sortedBy { it.order }))
    }

    /** Replaces an existing exercise (e.g. after recording/editing one of its sets). */
    @Suppress("ReturnCount")
    fun withUpdatedExercise(updated: WorkoutExercise): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        if (exercises.none { it.id == updated.id }) {
            return DomainResult.Failure(WorkoutValidationError.ExerciseNotFound)
        }
        return DomainResult.Success(
            copy(exercises = exercises.map { if (it.id == updated.id) updated else it }),
        )
    }

    /** Marks the session [WorkoutSessionStatus.COMPLETED] at [endedAt]. Terminal; cannot be undone. */
    fun complete(endedAt: Instant): DomainResult<WorkoutSession, WorkoutValidationError> = end(WorkoutSessionStatus.COMPLETED, endedAt)

    /** Marks the session [WorkoutSessionStatus.ABANDONED] at [endedAt]. Terminal; cannot be undone. */
    fun abandon(endedAt: Instant): DomainResult<WorkoutSession, WorkoutValidationError> = end(WorkoutSessionStatus.ABANDONED, endedAt)

    @Suppress("ReturnCount")
    private fun end(
        status: WorkoutSessionStatus,
        endedAt: Instant,
    ): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        if (endedAt < startedAt) {
            return DomainResult.Failure(WorkoutValidationError.EndedBeforeStarted)
        }
        return DomainResult.Success(copy(status = status, endedAt = endedAt, restTimer = null))
    }

    /** Starts (or replaces) the session's rest timer. Only valid while [WorkoutSessionStatus.ACTIVE]. */
    fun withStartedRestTimer(timer: RestTimer): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(copy(restTimer = timer))
    }

    /** Replaces the rest timer with an adjusted one (e.g. after add/remove time). Only valid while [WorkoutSessionStatus.ACTIVE]. */
    fun withAdjustedRestTimer(timer: RestTimer): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(copy(restTimer = timer))
    }

    /** Clears (skips) the rest timer, if any. Only valid while [WorkoutSessionStatus.ACTIVE]. */
    fun withClearedRestTimer(): DomainResult<WorkoutSession, WorkoutValidationError> {
        requireActive()?.let { return DomainResult.Failure(it) }
        return DomainResult.Success(copy(restTimer = null))
    }

    private fun requireActive(): WorkoutValidationError? =
        if (status != WorkoutSessionStatus.ACTIVE) WorkoutValidationError.SessionNotActive else null

    /** Whether this session has been marked invalid (see [invalidate]). */
    val isInvalidated: Boolean get() = invalidatedAt != null

    /**
     * Marks a [WorkoutSessionStatus.COMPLETED] session invalidated at [at] -
     * a correction for a wrongly recorded workout, excluding it from history
     * and future progression input without a destructive delete. Mirrors
     * [com.repflow.app.domain.exercise.Exercise.archive]'s
     * "excluded, never deleted" shape.
     */
    fun invalidate(at: Instant): DomainResult<WorkoutSession, WorkoutValidationError> {
        if (status != WorkoutSessionStatus.COMPLETED) {
            return DomainResult.Failure(WorkoutValidationError.InvalidatedAtNotAllowedForIncompleteSession)
        }
        if (endedAt != null && at < endedAt) {
            return DomainResult.Failure(WorkoutValidationError.InvalidatedBeforeEnded)
        }
        return DomainResult.Success(copy(invalidatedAt = at))
    }
}
