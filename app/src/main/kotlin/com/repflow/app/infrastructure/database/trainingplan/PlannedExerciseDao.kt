package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Room data access for [PlannedExerciseEntity].
 *
 * Rows are insert-only, in bulk, alongside their owning
 * [TrainingPlanVersionEntity] - there is no update or per-row insert used by
 * the repository (see
 * [com.repflow.app.data.trainingplan.LocalTrainingPlanRepository]).
 */
@Dao
interface PlannedExerciseDao {
    @Query("SELECT * FROM planned_exercises WHERE version_id = :versionId ORDER BY sort_order ASC")
    suspend fun findAllForVersion(versionId: String): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE id = :id")
    suspend fun findById(id: String): PlannedExerciseEntity?

    @Insert
    suspend fun insertAll(entities: List<PlannedExerciseEntity>)
}
