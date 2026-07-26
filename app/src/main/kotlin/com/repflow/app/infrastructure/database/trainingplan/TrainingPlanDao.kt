package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room data access for [TrainingPlanEntity]. Mirrors
 * [com.repflow.app.infrastructure.database.exercise.ExerciseDao]'s shape:
 * insert and update are separate, never an upsert, and no delete exists yet.
 */
@Dao
interface TrainingPlanDao {
    @Query("SELECT * FROM training_plans ORDER BY name_key ASC, id ASC")
    fun observeAll(): Flow<List<TrainingPlanEntity>>

    /** Every training plan, for backup export. */
    @Query("SELECT * FROM training_plans ORDER BY name_key ASC, id ASC")
    suspend fun findAll(): List<TrainingPlanEntity>

    @Query("SELECT * FROM training_plans WHERE id = :id")
    suspend fun findById(id: String): TrainingPlanEntity?

    @Query("SELECT id FROM training_plans WHERE name_key = :nameKey")
    suspend fun findIdByNameKey(nameKey: String): String?

    @Insert
    suspend fun insert(entity: TrainingPlanEntity)

    @Update
    suspend fun update(entity: TrainingPlanEntity): Int
}
