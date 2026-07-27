package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import java.time.Instant
import java.time.LocalDate

/** An in-memory [FutsalRepository] fake for use-case tests. */
class InMemoryFutsalRepository : FutsalRepository {
    private val byDate = mutableMapOf<LocalDate, FutsalSession>()

    var nextUpsertFailure: FutsalPersistenceError? = null

    fun seed(session: FutsalSession) {
        byDate[session.date] = session
    }

    override suspend fun findForDate(date: LocalDate): FutsalSession? = byDate[date]

    override suspend fun findSince(since: Instant): List<FutsalSession> {
        val sinceDate = LocalDate.ofEpochDay(since.epochSecond / SECONDS_PER_DAY)
        return byDate.values.filter { !it.date.isBefore(sinceDate) }
    }

    override suspend fun findAll(): List<FutsalSession> = byDate.values.sortedByDescending { it.date }

    override suspend fun upsert(session: FutsalSession): DomainResult<Unit, FutsalPersistenceError> {
        nextUpsertFailure?.let {
            nextUpsertFailure = null
            return DomainResult.Failure(it)
        }
        byDate[session.date] = session
        return DomainResult.Success(Unit)
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
