package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * An in-memory [ExerciseRepository] fake for use-case and ViewModel tests.
 *
 * Mimics the real repository's uniqueness semantics (a `name_key` conflict
 * fails [insert] and [update] with [ExercisePersistenceError.DuplicateName],
 * spanning active and archived rows per D-9) and the distinction between
 * [insert] (fails if the id already exists) and [update] (fails if it does
 * not) - see the plan's additional implementation corrections, items 3-4.
 *
 * [nextInsertFailure] / [nextUpdateFailure] let a test force the next call to
 * fail as the repository would after losing a race to the database's
 * `UNIQUE` constraint, even though the use case's own precheck passed.
 *
 * [observeFailureOnNextSubscription] lets a test simulate a Room `Flow` that
 * throws on collection (D-24/D-28) - consumed exactly once per subscription,
 * so a subsequent resubscription (e.g. after a ViewModel's retry) succeeds
 * normally.
 */
class InMemoryExerciseRepository : ExerciseRepository {
    private val state = MutableStateFlow<Map<ExerciseId, Exercise>>(emptyMap())

    var nextInsertFailure: ExercisePersistenceError? = null
    var nextUpdateFailure: ExercisePersistenceError? = null
    var observeFailureOnNextSubscription: Boolean = false

    fun seed(exercise: Exercise) {
        state.value = state.value + (exercise.id to exercise)
    }

    override fun observe(criteria: ExerciseQueryCriteria): Flow<List<Exercise>> =
        flow {
            if (observeFailureOnNextSubscription) {
                observeFailureOnNextSubscription = false
                error("Simulated observation failure")
            }
            emitAll(
                state.map { exercises ->
                    exercises.values
                        .filter { matchesStatus(it, criteria) && matchesQuery(it, criteria) }
                        .sortedWith(compareBy({ it.name.key }, { it.id.value }))
                },
            )
        }

    override suspend fun findById(id: ExerciseId): Exercise? = state.value[id]

    override suspend fun findIdByNameKey(nameKey: String): ExerciseId? =
        state.value.values
            .firstOrNull { it.name.key == nameKey }
            ?.id

    override suspend fun findAll(): List<Exercise> = state.value.values.sortedWith(compareBy({ it.name.key }, { it.id.value }))

    // Guard-clause-style early returns (see the `@Suppress` rationale on
    // `CreateExercise.invoke`); `?.let { return }` isn't recognized by detekt's
    // narrow structural guard-clause detector.
    @Suppress("ReturnCount")
    override suspend fun insert(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError> {
        nextInsertFailure?.let {
            nextInsertFailure = null
            return DomainResult.Failure(it)
        }
        if (state.value.containsKey(exercise.id)) {
            return DomainResult.Failure(ExercisePersistenceError.Unavailable)
        }
        if (hasConflictingNameKey(exercise)) {
            return DomainResult.Failure(ExercisePersistenceError.DuplicateName)
        }
        state.value = state.value + (exercise.id to exercise)
        return DomainResult.Success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun update(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError> {
        nextUpdateFailure?.let {
            nextUpdateFailure = null
            return DomainResult.Failure(it)
        }
        if (!state.value.containsKey(exercise.id)) {
            return DomainResult.Failure(ExercisePersistenceError.Unavailable)
        }
        if (hasConflictingNameKey(exercise)) {
            return DomainResult.Failure(ExercisePersistenceError.DuplicateName)
        }
        state.value = state.value + (exercise.id to exercise)
        return DomainResult.Success(Unit)
    }

    private fun hasConflictingNameKey(exercise: Exercise): Boolean =
        state.value.values.any { it.id != exercise.id && it.name.key == exercise.name.key }

    private fun matchesStatus(
        exercise: Exercise,
        criteria: ExerciseQueryCriteria,
    ): Boolean =
        when (criteria.status) {
            ExerciseStatusFilter.ACTIVE -> !exercise.isArchived
            ExerciseStatusFilter.ARCHIVED -> exercise.isArchived
        }

    private fun matchesQuery(
        exercise: Exercise,
        criteria: ExerciseQueryCriteria,
    ): Boolean = criteria.normalizedQuery.isEmpty() || exercise.name.key.contains(criteria.normalizedQuery)
}
