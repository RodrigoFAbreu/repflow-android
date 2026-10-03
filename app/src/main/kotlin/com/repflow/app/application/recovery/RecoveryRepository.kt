package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * The application's capability contract for persisting and querying
 * recovery entries. Implemented by the infrastructure/data layer; no Room
 * type is visible here, mirroring
 * [com.repflow.app.application.exercise.ExerciseRepository].
 *
 * At most one row exists per date - [upsert] replaces any existing row for
 * [RecoveryEntry.date] rather than requiring separate insert/update calls,
 * since there is no uniqueness concern beyond the date itself.
 */
interface RecoveryRepository {
    suspend fun findForDate(date: LocalDate): RecoveryEntry?

    /**
     * The entry for [date], or `null`, re-emitted whenever it is written -
     * the observable twin of [findForDate], for the readiness score
     * (remediation-1 CP4).
     */
    fun observeForDate(date: LocalDate): Flow<RecoveryEntry?>

    suspend fun findLatest(): RecoveryEntry?

    /** Every recovery entry, most recent first, for backup export. */
    suspend fun findAll(): List<RecoveryEntry>

    suspend fun upsert(entry: RecoveryEntry): DomainResult<Unit, RecoveryPersistenceError>
}
