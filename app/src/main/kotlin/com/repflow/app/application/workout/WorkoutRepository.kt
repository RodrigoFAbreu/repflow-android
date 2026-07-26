package com.repflow.app.application.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.flow.Flow

/**
 * The application's capability contract for persisting and querying workout
 * sessions. Implemented by the infrastructure/data layer; no Room type is
 * visible here (see `LayerBoundaryTest`), mirroring
 * [com.repflow.app.application.trainingplan.TrainingPlanRepository]'s shape.
 *
 * There is deliberately no method to fetch "all sessions" here yet - history
 * browsing is out of scope until Milestone 7; only the single current/active
 * session and lookup-by-id are needed for Milestone 3.
 */
interface WorkoutRepository {
    /** Emits the current [WorkoutSessionStatus.ACTIVE][com.repflow.app.domain.workout.WorkoutSessionStatus.ACTIVE] session, or `null` if none exists. */
    fun observeActiveSession(): Flow<WorkoutSession?>

    suspend fun findActiveSession(): WorkoutSession?

    suspend fun findById(id: WorkoutSessionId): WorkoutSession?

    /** Inserts a brand-new session. Fails with [WorkoutPersistenceError.ActiveSessionAlreadyExists] if one is already active. */
    suspend fun insert(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError>

    /** Persists every mutable field of an existing session (status, `endedAt`, exercises, sets). */
    suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError>
}
