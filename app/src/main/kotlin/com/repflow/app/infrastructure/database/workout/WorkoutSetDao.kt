package com.repflow.app.infrastructure.database.workout

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room data access for [WorkoutSetEntity]. Rows are (re)inserted in bulk
 * alongside their owning [WorkoutExerciseEntity] rows - see
 * [WorkoutExerciseDao]'s KDoc for the "replace the whole tree" rationale.
 */
@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY sort_order ASC")
    suspend fun findAllForExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    @Insert
    suspend fun insertAll(entities: List<WorkoutSetEntity>)
}
