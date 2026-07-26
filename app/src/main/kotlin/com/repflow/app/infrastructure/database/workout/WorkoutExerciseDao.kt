package com.repflow.app.infrastructure.database.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room data access for [WorkoutExerciseEntity].
 *
 * [deleteForSession] + [insertAll] together implement "replace the whole
 * exercise/set tree for a session" on every
 * [com.repflow.app.data.workout.LocalWorkoutRepository.update] call - simpler
 * and safe at this row count than diffing individual rows, and relies on the
 * `ON DELETE CASCADE` to `workout_sets` to remove child set rows too.
 */
@Dao
interface WorkoutExerciseDao {
    @Query("SELECT * FROM workout_exercises WHERE session_id = :sessionId ORDER BY sort_order ASC")
    suspend fun findAllForSession(sessionId: String): List<WorkoutExerciseEntity>

    @Insert
    suspend fun insertAll(entities: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises WHERE session_id = :sessionId")
    suspend fun deleteForSession(sessionId: String)
}
