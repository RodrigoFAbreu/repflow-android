package com.repflow.app.data.recovery

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.repflow.app.application.recovery.FutsalPersistenceError
import com.repflow.app.application.recovery.FutsalRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.infrastructure.database.recovery.FutsalSessionDao
import com.repflow.app.infrastructure.database.recovery.FutsalSessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

/** A mapping failure surfaced as a runtime exception, mirroring [RecoveryMappingException]. */
class FutsalMappingException(
    error: FutsalMappingError,
) : IllegalStateException("Futsal session ${error.entityId} failed to map: ${error.error}")

/**
 * The [FutsalRepository] implementation backed by Room, via
 * [FutsalSessionDao], mirroring [LocalRecoveryRepository].
 */
class LocalFutsalRepository
    @Inject
    constructor(
        private val dao: FutsalSessionDao,
    ) : FutsalRepository {
        override suspend fun findForDate(date: LocalDate): FutsalSession? = dao.findForDate(date.toString())?.let(::toDomainOrThrow)

        override suspend fun findSince(since: Instant): List<FutsalSession> {
            val sinceDate = since.atZone(ZoneOffset.UTC).toLocalDate()
            return dao.findSince(sinceDate.toString()).map(::toDomainOrThrow)
        }

        @Suppress("ReturnCount")
        override suspend fun upsert(session: FutsalSession): DomainResult<Unit, FutsalPersistenceError> {
            try {
                dao.upsert(FutsalSessionMapper.toEntity(session))
                return DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(FutsalPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(FutsalPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(FutsalPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(FutsalPersistenceError.Unavailable)
            }
        }

        private fun toDomainOrThrow(entity: FutsalSessionEntity): FutsalSession =
            when (val result = FutsalSessionMapper.toDomain(entity)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw FutsalMappingException(result.error)
            }
    }
