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
 * [observeCompletedSessions] was added in Milestone 7 for history browsing and
 * CSV export, and [findAllForBackup] reads every session for a backup; before
 * those, only the single current/active session and lookup-by-id were needed.
 */
interface WorkoutRepository {
    /** Emits the current [WorkoutSessionStatus.ACTIVE][com.repflow.app.domain.workout.WorkoutSessionStatus.ACTIVE] session, or `null` if none exists. */
    fun observeActiveSession(): Flow<WorkoutSession?>

    suspend fun findActiveSession(): WorkoutSession?

    suspend fun findById(id: WorkoutSessionId): WorkoutSession?

    /**
     * Every completed session, most recently ended first, for history
     * browsing and CSV export. [includeInvalidated] controls whether an
     * invalidated session (Milestone 8, CP11) is included - the CSV export must
     * pass `true` so a correction is never silently dropped from a user's
     * data; History defaults its own show/hide filter to `false`.
     */
    fun observeCompletedSessions(includeInvalidated: Boolean): Flow<List<WorkoutSession>>

    /**
     * Every session in every status, oldest first, each with its exercises and
     * sets, for backup export. Nothing is filtered, so a backup never silently
     * drops a running, abandoned or invalidated session. Throws if a row can't
     * be mapped rather than skipping it.
     */
    suspend fun findAllForBackup(): List<WorkoutSession>

    /** Inserts a brand-new session. Fails with [WorkoutPersistenceError.ActiveSessionAlreadyExists] if one is already active. */
    suspend fun insert(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError>

    /** Persists every mutable field of an existing session (status, `endedAt`, exercises, sets). */
    suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError>
}
