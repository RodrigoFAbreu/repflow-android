package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room data access for [TrainingPlanVersionEntity].
 *
 * There is deliberately no update method - versions are insert-only, never
 * mutated once created (see [TrainingPlanVersionEntity]'s KDoc).
 */
@Dao
interface TrainingPlanVersionDao {
    @Query("SELECT * FROM training_plan_versions WHERE plan_id = :planId ORDER BY version_number DESC LIMIT 1")
    suspend fun findLatestForPlan(planId: String): TrainingPlanVersionEntity?

    @Query("SELECT * FROM training_plan_versions WHERE plan_id = :planId ORDER BY version_number ASC")
    suspend fun findAllForPlan(planId: String): List<TrainingPlanVersionEntity>

    @Insert
    suspend fun insert(entity: TrainingPlanVersionEntity)
}
