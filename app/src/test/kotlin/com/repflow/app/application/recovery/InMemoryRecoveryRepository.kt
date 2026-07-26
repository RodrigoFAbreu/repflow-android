package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import java.time.LocalDate

/** An in-memory [RecoveryRepository] fake for use-case tests. */
class InMemoryRecoveryRepository : RecoveryRepository {
    private val byDate = mutableMapOf<LocalDate, RecoveryEntry>()

    var nextUpsertFailure: RecoveryPersistenceError? = null

    fun seed(entry: RecoveryEntry) {
        byDate[entry.date] = entry
    }

    override suspend fun findForDate(date: LocalDate): RecoveryEntry? = byDate[date]

    override suspend fun findLatest(): RecoveryEntry? = byDate.values.maxByOrNull { it.date }

    override suspend fun upsert(entry: RecoveryEntry): DomainResult<Unit, RecoveryPersistenceError> {
        nextUpsertFailure?.let {
            nextUpsertFailure = null
            return DomainResult.Failure(it)
        }
        byDate[entry.date] = entry
        return DomainResult.Success(Unit)
    }
}
