package com.repflow.app.data.trainingplan

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.application.trainingplan.TrainingPlanPersistenceError
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The [TrainingPlanRepository] implementation backed by Room, via
 * [TrainingPlanDao], [TrainingPlanVersionDao] and [PlannedExerciseDao].
 *
 * [database] is injected directly (rather than only the three DAOs) solely
 * to obtain [androidx.room.withTransaction] for [createPlanWithFirstVersion]
 * and [addVersion] - both writes span all three tables and must be atomic.
 * Exception translation is narrow and explicit, mirroring
 * [com.repflow.app.data.exercise.LocalExerciseRepository]: only the listed
 * `SQLiteException` subtypes are caught, `CancellationException` is never
 * one of them, and there is no `runCatching` or broad `catch (e: Exception)`
 * anywhere here.
 */
class LocalTrainingPlanRepository
    @Inject
    constructor(
        private val database: RepFlowDatabase,
        private val planDao: TrainingPlanDao,
        private val versionDao: TrainingPlanVersionDao,
        private val plannedExerciseDao: PlannedExerciseDao,
    ) : TrainingPlanRepository {
        override fun observeOverviews(): Flow<List<TrainingPlanOverview>> =
            planDao.observeAll().map { plans -> plans.map { plan -> toOverviewOrThrow(plan) } }

        override suspend fun findOverviewByPlanId(id: TrainingPlanId): TrainingPlanOverview? =
            planDao.findById(id.value)?.let { plan -> toOverviewOrThrow(plan) }

        override suspend fun findPlanIdByNameKey(nameKey: String): TrainingPlanId? = planDao.findIdByNameKey(nameKey)?.let(::TrainingPlanId)

        @Suppress("ReturnCount")
        override suspend fun createPlanWithFirstVersion(
            plan: TrainingPlan,
            version: TrainingPlanVersion,
        ): DomainResult<Unit, TrainingPlanPersistenceError> {
            try {
                database.withTransaction {
                    planDao.insert(TrainingPlanEntityMapper.toEntity(plan))
                    versionDao.insert(TrainingPlanEntityMapper.toEntity(version))
                    plannedExerciseDao.insertAll(TrainingPlanEntityMapper.toEntities(version))
                }
                return DomainResult.Success(Unit)
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            }
        }

        @Suppress("ReturnCount")
        override suspend fun addVersion(
            plan: TrainingPlan,
            version: TrainingPlanVersion,
        ): DomainResult<Unit, TrainingPlanPersistenceError> {
            try {
                var rowsUpdated = 0
                database.withTransaction {
                    rowsUpdated = planDao.update(TrainingPlanEntityMapper.toEntity(plan))
                    versionDao.insert(TrainingPlanEntityMapper.toEntity(version))
                    plannedExerciseDao.insertAll(TrainingPlanEntityMapper.toEntities(version))
                }
                return if (rowsUpdated == 0) {
                    DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
                } else {
                    DomainResult.Success(Unit)
                }
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(TrainingPlanPersistenceError.Unavailable)
            }
        }

        private suspend fun toOverviewOrThrow(planEntity: TrainingPlanEntity): TrainingPlanOverview {
            val plan = requireMapped(TrainingPlanEntityMapper.toDomain(planEntity))
            val latestVersionEntity =
                versionDao.findLatestForPlan(planEntity.id)
                    ?: throw TrainingPlanMappingException(
                        TrainingPlanMappingError.InvalidFields(planEntity.id, emptyList()),
                    )
            val plannedExerciseRows = plannedExerciseDao.findAllForVersion(latestVersionEntity.id)
            val latestVersion = requireMapped(TrainingPlanEntityMapper.toDomain(latestVersionEntity, plannedExerciseRows))
            return TrainingPlanOverview(plan = plan, latestVersion = latestVersion)
        }

        private fun <T> requireMapped(result: DomainResult<T, TrainingPlanMappingError>): T =
            when (result) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw TrainingPlanMappingException(result.error)
            }
    }

/**
 * `training_plans.name_key` is the only unique-constrained column written
 * directly by this repository (`training_plan_versions`'s
 * `(plan_id, version_number)` uniqueness is guaranteed by construction -
 * [com.repflow.app.application.trainingplan.ReviseTrainingPlan] always reads
 * the latest version number first, so a collision there would indicate a
 * bug, not a legitimate concurrent-write case to translate). SQLite's own
 * `UNIQUE constraint failed` message names the violated table and column,
 * mirroring [com.repflow.app.data.exercise.translateConstraintViolation].
 */
internal fun translateConstraintViolation(exception: SQLiteConstraintException): TrainingPlanPersistenceError =
    if (exception.message?.contains("training_plans.name_key") == true) {
        TrainingPlanPersistenceError.DuplicateName
    } else {
        TrainingPlanPersistenceError.Unavailable
    }
