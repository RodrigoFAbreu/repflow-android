package com.repflow.app.application.progression

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId

/** An in-memory [ProgressionRecommendationRepository] fake for use-case tests. */
class InMemoryProgressionRecommendationRepository : ProgressionRecommendationRepository {
    private val recommendations = mutableMapOf<ProgressionRecommendationId, ProgressionRecommendation>()

    var nextInsertFailure: ProgressionPersistenceError? = null
    var nextUpdateFailure: ProgressionPersistenceError? = null

    override suspend fun findLatestForExercise(exerciseId: ExerciseId): ProgressionRecommendation? =
        recommendations.values.filter { it.exerciseId == exerciseId }.maxByOrNull { it.computedAt }

    override suspend fun findAll(): List<ProgressionRecommendation> = recommendations.values.sortedByDescending { it.computedAt }

    override suspend fun insert(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError> {
        nextInsertFailure?.let {
            nextInsertFailure = null
            return DomainResult.Failure(it)
        }
        recommendations[recommendation.id] = recommendation
        return DomainResult.Success(Unit)
    }

    @Suppress("ReturnCount")
    override suspend fun update(recommendation: ProgressionRecommendation): DomainResult<Unit, ProgressionPersistenceError> {
        nextUpdateFailure?.let {
            nextUpdateFailure = null
            return DomainResult.Failure(it)
        }
        if (recommendation.id !in recommendations) return DomainResult.Failure(ProgressionPersistenceError.Unavailable)
        recommendations[recommendation.id] = recommendation
        return DomainResult.Success(Unit)
    }

    /** The row goes while the use case holds what it read, as a restore can do. */
    fun removeAll() {
        recommendations.clear()
    }
}
