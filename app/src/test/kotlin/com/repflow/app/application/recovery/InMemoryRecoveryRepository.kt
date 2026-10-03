package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** An in-memory [RecoveryRepository] fake for use-case tests. */
class InMemoryRecoveryRepository : RecoveryRepository {
    private val byDate = MutableStateFlow<Map<LocalDate, RecoveryEntry>>(emptyMap())

    var nextUpsertFailure: RecoveryPersistenceError? = null

    fun seed(entry: RecoveryEntry) {
        byDate.update { it + (entry.date to entry) }
    }

    override suspend fun findForDate(date: LocalDate): RecoveryEntry? = byDate.value[date]

    /** Like Room's `Flow`, re-emits on every write to any date, not only [date]'s. */
    override fun observeForDate(date: LocalDate): Flow<RecoveryEntry?> = byDate.map { it[date] }

    override suspend fun findLatest(): RecoveryEntry? = byDate.value.values.maxByOrNull { it.date }

    override suspend fun findAll(): List<RecoveryEntry> = byDate.value.values.sortedByDescending { it.date }

    override suspend fun upsert(entry: RecoveryEntry): DomainResult<Unit, RecoveryPersistenceError> {
        nextUpsertFailure?.let {
            nextUpsertFailure = null
            return DomainResult.Failure(it)
        }
        byDate.update { it + (entry.date to entry) }
        return DomainResult.Success(Unit)
    }
}
