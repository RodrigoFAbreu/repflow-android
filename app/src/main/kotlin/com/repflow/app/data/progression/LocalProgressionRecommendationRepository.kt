package com.repflow.app.data.progression

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.repflow.app.application.progression.ProgressionPersistenceError
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationDao
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import javax.inject.Inject

/** A mapping failure surfaced as a runtime exception, mirroring the recovery layer's `RecoveryMappingException`. */
class ProgressionMappingException(
    error: ProgressionMappingError,
) : IllegalStateException("Progression recommendation ${error.entityId} failed to map: ${error.error}")

/**
 * The [ProgressionRecommendationRepository] implementation backed by
 * Room, via [ProgressionRecommendationDao]. Exception translation mirrors
 * [com.repflow.app.data.recovery.LocalRecoveryRepository].
 */
class LocalProgressionRecommendationRepository
    @Inject
    constructor(
        private val dao: ProgressionRecommendationDao,
    ) : ProgressionRecommendationRepository {
        override suspend fun findLatestForExercise(exerciseId: ExerciseId): ProgressionRecommendation? =
            dao.findLatestForExercise(exerciseId.value)?.let(::toDomainOrThrow)

        override suspend fun findAll(): List<ProgressionRecommendation> = dao.findAll().map(::toDomainOrThrow)

        override suspend fun insert(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError> =
            runCatchingPersistence { dao.insert(ProgressionRecommendationMapper.toEntity(recommendation)) }

        override suspend fun update(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError> =
            runCatchingPersistence { dao.update(ProgressionRecommendationMapper.toEntity(recommendation)) }

        @Suppress("ReturnCount")
        private inline fun runCatchingPersistence(block: () -> Unit): DomainResult<Unit, ProgressionPersistenceError> {
            try {
                block()
                return DomainResult.Success(Unit)
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(ProgressionPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(ProgressionPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(ProgressionPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(ProgressionPersistenceError.Unavailable)
            }
        }

        private fun toDomainOrThrow(entity: ProgressionRecommendationEntity): ProgressionRecommendation =
            when (val result = ProgressionRecommendationMapper.toDomain(entity)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw ProgressionMappingException(result.error)
            }
    }
