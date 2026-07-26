package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import kotlinx.coroutines.flow.Flow

/**
 * The application's capability contract for persisting and querying
 * exercises. Implemented by the infrastructure/data layer; no Room type is
 * visible here (see the layer boundary rules in `LayerBoundaryTest`).
 *
 * There is deliberately no `deleteById` and no reference-checking hook -
 * nothing in Milestone 1 references an exercise yet (see plan.md D-12).
 *
 * [insert] and [update] are kept separate rather than a single generic
 * "upsert" so that update can never silently create a row for a missing id
 * (see plan.md's additional implementation corrections, item 4).
 */
interface ExerciseRepository {
    fun observe(criteria: ExerciseQueryCriteria): Flow<List<Exercise>>

    suspend fun findById(id: ExerciseId): Exercise?

    suspend fun findIdByNameKey(nameKey: String): ExerciseId?

    suspend fun insert(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError>

    suspend fun update(exercise: Exercise): DomainResult<Unit, ExercisePersistenceError>
}
