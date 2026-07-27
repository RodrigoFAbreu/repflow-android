package com.repflow.app.application.recovery

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import java.time.Instant
import java.time.LocalDate

/**
 * The application's capability contract for persisting and querying futsal
 * sessions, mirroring [RecoveryRepository].
 *
 * At most one row exists per date - [upsert] replaces any existing row for
 * [FutsalSession.date].
 */
interface FutsalRepository {
    suspend fun findForDate(date: LocalDate): FutsalSession?

    /** Sessions with [FutsalSession.date] on or after [since]'s date, most recent first. */
    suspend fun findSince(since: Instant): List<FutsalSession>

    /** Every futsal session, most recent first, for backup export. */
    suspend fun findAll(): List<FutsalSession>

    suspend fun upsert(session: FutsalSession): DomainResult<Unit, FutsalPersistenceError>
}
