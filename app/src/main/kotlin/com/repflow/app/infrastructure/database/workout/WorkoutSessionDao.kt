package com.repflow.app.infrastructure.database.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room data access for [WorkoutSessionEntity]. Mirrors
 * [com.repflow.app.infrastructure.database.trainingplan.TrainingPlanDao]'s
 * shape: insert and update are separate, never an upsert, no delete exists
 * yet (history browsing/pruning is out of scope until Milestone 7).
 */
@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun findActive(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun findById(id: String): WorkoutSessionEntity?

    /** Every completed session, most recently ended first, for history browsing and backup export. */
    @Query("SELECT * FROM workout_sessions WHERE status = 'COMPLETED' ORDER BY ended_at DESC")
    fun observeCompleted(): Flow<List<WorkoutSessionEntity>>

    @Insert
    suspend fun insert(entity: WorkoutSessionEntity)

    @Update
    suspend fun update(entity: WorkoutSessionEntity): Int
}
