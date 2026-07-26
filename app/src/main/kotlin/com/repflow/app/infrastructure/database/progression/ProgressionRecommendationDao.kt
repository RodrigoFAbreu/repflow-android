package com.repflow.app.infrastructure.database.progression

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** Room data access for [ProgressionRecommendationEntity]. */
@Dao
interface ProgressionRecommendationDao {
    @Query(
        "SELECT * FROM progression_recommendations WHERE exercise_id = :exerciseId " +
            "ORDER BY computed_at DESC LIMIT 1",
    )
    suspend fun findLatestForExercise(exerciseId: String): ProgressionRecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ProgressionRecommendationEntity)

    @Update
    suspend fun update(entity: ProgressionRecommendationEntity)
}
