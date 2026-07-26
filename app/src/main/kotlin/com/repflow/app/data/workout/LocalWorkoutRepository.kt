package com.repflow.app.data.workout

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.repflow.app.application.workout.WorkoutPersistenceError
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseDao
import com.repflow.app.infrastructure.database.workout.WorkoutSessionDao
import com.repflow.app.infrastructure.database.workout.WorkoutSessionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The [WorkoutRepository] implementation backed by Room, via
 * [WorkoutSessionDao], [WorkoutExerciseDao] and [WorkoutSetDao].
 *
 * [update] replaces the session's whole exercise/set tree on every call
 * (delete-then-reinsert, relying on cascading deletes) rather than diffing
 * individual rows - see [WorkoutExerciseDao]'s KDoc for the rationale.
 * Exception translation is narrow and explicit, mirroring
 * [com.repflow.app.data.trainingplan.LocalTrainingPlanRepository]: only the
 * listed `SQLiteException` subtypes are caught, `CancellationException` is
 * never one of them, and there is no `runCatching` or broad
 * `catch (e: Exception)` anywhere here.
 */
class LocalWorkoutRepository
    @Inject
    constructor(
        private val database: RepFlowDatabase,
        private val sessionDao: WorkoutSessionDao,
        private val exerciseDao: WorkoutExerciseDao,
        private val setDao: WorkoutSetDao,
    ) : WorkoutRepository {
        override fun observeActiveSession(): Flow<WorkoutSession?> =
            sessionDao.observeActive().map { entity -> entity?.let { toSessionOrThrow(it) } }

        override suspend fun findActiveSession(): WorkoutSession? = sessionDao.findActive()?.let { toSessionOrThrow(it) }

        override suspend fun findById(id: WorkoutSessionId): WorkoutSession? = sessionDao.findById(id.value)?.let { toSessionOrThrow(it) }

        @Suppress("ReturnCount")
        override suspend fun insert(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
            try {
                if (sessionDao.findActive() != null) {
                    return DomainResult.Failure(WorkoutPersistenceError.ActiveSessionAlreadyExists)
                }
                database.withTransaction {
                    sessionDao.insert(WorkoutEntityMapper.toSessionEntity(session))
                    exerciseDao.insertAll(WorkoutEntityMapper.toExerciseEntities(session))
                    setDao.insertAll(WorkoutEntityMapper.toSetEntities(session))
                }
                return DomainResult.Success(Unit)
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            }
        }

        @Suppress("ReturnCount")
        override suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
            try {
                var rowsUpdated = 0
                database.withTransaction {
                    rowsUpdated = sessionDao.update(WorkoutEntityMapper.toSessionEntity(session))
                    exerciseDao.deleteForSession(session.id.value)
                    exerciseDao.insertAll(WorkoutEntityMapper.toExerciseEntities(session))
                    setDao.insertAll(WorkoutEntityMapper.toSetEntities(session))
                }
                return if (rowsUpdated == 0) {
                    DomainResult.Failure(WorkoutPersistenceError.NotFound)
                } else {
                    DomainResult.Success(Unit)
                }
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(WorkoutPersistenceError.Unavailable)
            }
        }

        private suspend fun toSessionOrThrow(entity: WorkoutSessionEntity): WorkoutSession {
            val exerciseRows = exerciseDao.findAllForSession(entity.id)
            val setRowsByExerciseId = exerciseRows.associate { it.id to setDao.findAllForExercise(it.id) }
            return when (val result = WorkoutEntityMapper.toDomain(entity, exerciseRows, setRowsByExerciseId)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw WorkoutMappingException(result.error)
            }
        }
    }

/**
 * `workout_sessions` itself carries no unique-constrained column beyond its
 * primary key. The only unique indices in the workout schema are the
 * `(session_id, sort_order)` and `(workout_exercise_id, sort_order)` pairs
 * on `workout_exercises`/`workout_sets`, and those are only ever violated by
 * a repository-construction bug (sort order is always assigned sequentially
 * before insert), not a legitimate concurrent-write case to translate -
 * every constraint violation here is therefore unexpected and reported as
 * [WorkoutPersistenceError.Unavailable].
 */
internal fun translateConstraintViolation(
    @Suppress("UNUSED_PARAMETER") exception: SQLiteConstraintException,
): WorkoutPersistenceError = WorkoutPersistenceError.Unavailable
