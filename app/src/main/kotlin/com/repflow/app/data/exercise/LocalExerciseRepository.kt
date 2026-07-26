package com.repflow.app.data.exercise

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.repflow.app.application.exercise.ExercisePersistenceError
import com.repflow.app.application.exercise.ExerciseQueryCriteria
import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The [ExerciseRepository] implementation backed by Room, via [ExerciseDao].
 *
 * Exception translation is narrow and explicit (see plan.md section G and
 * additional implementation correction 7): only the listed `SQLiteException`
 * subtypes are caught, `CancellationException` is never one of them, and
 * there is no `runCatching` or broad `catch (e: Exception)` anywhere here.
 */
class LocalExerciseRepository
    @Inject
    constructor(
        private val dao: ExerciseDao,
    ) : ExerciseRepository {
        override fun observe(criteria: ExerciseQueryCriteria): Flow<List<Exercise>> {
            val archived = criteria.status == ExerciseStatusFilter.ARCHIVED
            val likePattern = buildNameSearchPattern(criteria.normalizedQuery)
            return dao.observe(archived, likePattern).map { entities -> entities.map(::toDomainOrThrow) }
        }

        override suspend fun findById(id: ExerciseId): Exercise? = dao.findById(id.value)?.let(::toDomainOrThrow)

        override suspend fun findIdByNameKey(nameKey: String): ExerciseId? = dao.findIdByNameKey(nameKey)?.let(::ExerciseId)

        /**
         * Only the listed `SQLiteException` subtypes are caught (none of
         * which can be a `CancellationException`), and each failure is
         * translated to an opaque [ExercisePersistenceError] rather than
         * rethrown or logged - there is no logging abstraction in this
         * milestone to route it through, so the unused exception variables
         * below are named to match detekt's `SwallowedException` allowance
         * rather than suppressing the rule outright.
         */
        @Suppress("ReturnCount")
        override suspend fun insert(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError> {
            try {
                dao.insert(ExerciseEntityMapper.toEntity(exercise))
                return DomainResult.Success(Unit)
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            }
        }

        @Suppress("ReturnCount")
        override suspend fun update(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError> {
            try {
                val rowsUpdated = dao.update(ExerciseEntityMapper.toEntity(exercise))
                return if (rowsUpdated == 0) {
                    DomainResult.Failure(ExercisePersistenceError.Unavailable)
                } else {
                    DomainResult.Success(Unit)
                }
            } catch (e: SQLiteConstraintException) {
                return DomainResult.Failure(translateConstraintViolation(e))
            } catch (expected: SQLiteFullException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteDiskIOException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteDatabaseCorruptException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            } catch (expected: SQLiteException) {
                return DomainResult.Failure(ExercisePersistenceError.Unavailable)
            }
        }

        private fun toDomainOrThrow(entity: ExerciseEntity): Exercise =
            when (val result = ExerciseEntityMapper.toDomain(entity)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw ExerciseMappingException(result.error)
            }
    }

/**
 * `exercises.name_key` is the only unique-constrained column on `exercises`
 * (enforced by `index_exercises_name_key`, see
 * [com.repflow.app.infrastructure.database.exercise.ExerciseEntity]). SQLite's own
 * `UNIQUE constraint failed` message names the violated table and column
 * (e.g. `"UNIQUE constraint failed: exercises.name_key"`), never the index,
 * so that is the substring matched here - confirmed against a real device,
 * not just the JVM unit tests, since this message format is produced by the
 * native SQLite library rather than by Room or Kotlin code. Any other
 * constraint violation (there are none expected in this schema, but a
 * future one might exist) is treated as a generic unavailable failure rather
 * than guessed at.
 */
internal fun translateConstraintViolation(exception: SQLiteConstraintException): ExercisePersistenceError =
    if (exception.message?.contains("exercises.name_key") == true) {
        ExercisePersistenceError.DuplicateName
    } else {
        ExercisePersistenceError.Unavailable
    }
