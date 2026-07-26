package com.repflow.app.application.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [WorkoutRepository] fake for use-case tests, mirroring
 * [com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository]'s
 * shape.
 */
class InMemoryWorkoutRepository : WorkoutRepository {
    private val sessions = MutableStateFlow<Map<WorkoutSessionId, WorkoutSession>>(emptyMap())

    var nextInsertFailure: WorkoutPersistenceError? = null
    var nextUpdateFailure: WorkoutPersistenceError? = null

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessions.map { byId -> byId.values.firstOrNull { it.status == WorkoutSessionStatus.ACTIVE } }

    override suspend fun findActiveSession(): WorkoutSession? =
        sessions.value.values.firstOrNull { it.status == WorkoutSessionStatus.ACTIVE }

    override suspend fun findById(id: WorkoutSessionId): WorkoutSession? = sessions.value[id]

    override fun observeCompletedSessions(): Flow<List<WorkoutSession>> =
        sessions.map { byId ->
            byId.values
                .filter { it.status == WorkoutSessionStatus.COMPLETED }
                .sortedByDescending { it.endedAt }
        }

    @Suppress("ReturnCount")
    override suspend fun insert(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
        nextInsertFailure?.let {
            nextInsertFailure = null
            return DomainResult.Failure(it)
        }
        if (sessions.value.values.any { it.status == WorkoutSessionStatus.ACTIVE }) {
            return DomainResult.Failure(WorkoutPersistenceError.ActiveSessionAlreadyExists)
        }
        sessions.value = sessions.value + (session.id to session)
        return DomainResult.Success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
        nextUpdateFailure?.let {
            nextUpdateFailure = null
            return DomainResult.Failure(it)
        }
        if (!sessions.value.containsKey(session.id)) {
            return DomainResult.Failure(WorkoutPersistenceError.NotFound)
        }
        sessions.value = sessions.value + (session.id to session)
        return DomainResult.Success(Unit)
    }
}
