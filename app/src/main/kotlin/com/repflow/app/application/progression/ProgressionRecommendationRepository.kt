package com.repflow.app.application.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionRecommendation

/**
 * The application's capability contract for persisting and querying
 * progression recommendations. Implemented by the infrastructure/data
 * layer; no Room type is visible here, mirroring
 * [com.repflow.app.application.recovery.RecoveryRepository].
 */
interface ProgressionRecommendationRepository {
    suspend fun findLatestForExercise(exerciseId: ExerciseId): ProgressionRecommendation?

    suspend fun insert(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError>

    suspend fun update(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError>
}
