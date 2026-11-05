package com.repflow.app.application.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import kotlinx.coroutines.CompletableDeferred
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

    /**
     * How many [findById] and [findActiveSession] calls still succeed before
     * every later one throws [WorkoutReadFailedException]. `null` never fails;
     * `0` fails the next read.
     */
    var readsBeforeFailure: Int? = null

    /**
     * While set, [update] waits on it before anything else, as a write waits for
     * a running restore. Reads aren't held: on a normal phone, in WAL, they
     * return the old data at once.
     */
    var holdWrites: CompletableDeferred<Unit>? = null

    override fun observeActiveSession(): Flow<WorkoutSession?> =
        sessions.map { byId -> byId.values.firstOrNull { it.status == WorkoutSessionStatus.ACTIVE } }

    override suspend fun findActiveSession(): WorkoutSession? {
        failReadIfDue()
        return sessions.value.values.firstOrNull { it.status == WorkoutSessionStatus.ACTIVE }
    }

    override suspend fun findById(id: WorkoutSessionId): WorkoutSession? {
        failReadIfDue()
        return sessions.value[id]
    }

    override fun observeCompletedSessions(includeInvalidated: Boolean): Flow<List<WorkoutSession>> =
        sessions.map { byId ->
            byId.values
                .filter { it.status == WorkoutSessionStatus.COMPLETED && (includeInvalidated || !it.isInvalidated) }
                .sortedByDescending { it.endedAt }
        }

    override suspend fun findAllForBackup(): List<WorkoutSession> =
        sessions.value.values.sortedWith(compareBy({ it.startedAt }, { it.id.value }))

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
        holdWrites?.await()
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

    private fun failReadIfDue() {
        val remaining = readsBeforeFailure ?: return
        if (remaining == 0) throw WorkoutReadFailedException(IllegalStateException("simulated read failure"))
        readsBeforeFailure = remaining - 1
    }
}
