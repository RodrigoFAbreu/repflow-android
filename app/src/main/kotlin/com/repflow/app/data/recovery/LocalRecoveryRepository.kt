package com.repflow.app.data.recovery

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.repflow.app.application.recovery.RecoveryPersistenceError
import com.repflow.app.application.recovery.RecoveryRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryDao
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryEntity
import java.time.LocalDate
import javax.inject.Inject

/** A mapping failure surfaced as a runtime exception, mirroring the exercise layer's `ExerciseMappingException`. */
class RecoveryMappingException(
    error: RecoveryMappingError,
) : IllegalStateException("Recovery entry ${error.entityId} failed to map: ${error.error}")

/**
 * The [RecoveryRepository] implementation backed by Room, via
 * [RecoveryEntryDao]. Exception translation mirrors
 * [com.repflow.app.data.exercise.LocalExerciseRepository]: only the listed
 * `SQLiteException` subtypes are caught, never `CancellationException`.
 * There is no `name_key`-style unique-index conflict to translate here
 * ([RecoveryEntryDao.upsert] uses `OnConflictStrategy.REPLACE`).
 */
class LocalRecoveryRepository
    @Inject
    constructor(
        private val dao: RecoveryEntryDao,
    ) : RecoveryRepository {
        override suspend fun findForDate(date: LocalDate): RecoveryEntry? = dao.findForDate(date.toString())?.let(::toDomainOrThrow)

        override suspend fun findLatest(): RecoveryEntry? = dao.findLatest()?.let(::toDomainOrThrow)

        override suspend fun findAll(): List<RecoveryEntry> = dao.findAll().map(::toDomainOrThrow)

        @Suppress("ReturnCount")
        override suspend fun upsert(entry: RecoveryEntry): DomainResult<Unit, RecoveryPersistenceError> {
            try {
                dao.upsert(RecoveryEntryMapper.toEntity(entry))
                return DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(RecoveryPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(RecoveryPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(RecoveryPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(RecoveryPersistenceError.Unavailable)
            }
        }

        private fun toDomainOrThrow(entity: RecoveryEntryEntity): RecoveryEntry =
            when (val result = RecoveryEntryMapper.toDomain(entity)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw RecoveryMappingException(result.error)
            }
    }
