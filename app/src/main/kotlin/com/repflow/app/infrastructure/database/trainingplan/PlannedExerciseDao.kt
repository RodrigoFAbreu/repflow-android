package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    /**
     * Per exercise, the number of non-archived plans whose latest version
     * holds it (`TrainingPlanRepository.observeExercisePlanUsage`,
     * remediation-1 CP10). A read over the existing tables - no schema change.
     * Room re-emits on any write to the three tables it reads.
     */
    @Query(
        """
        SELECT pe.exercise_id AS exercise_id, COUNT(DISTINCT p.id) AS plan_count
        FROM planned_exercises pe
        INNER JOIN training_plan_versions v ON v.id = pe.version_id
        INNER JOIN training_plans p ON p.id = v.plan_id
        WHERE p.archived_at IS NULL
          AND v.version_number = (
            SELECT MAX(latest.version_number) FROM training_plan_versions latest WHERE latest.plan_id = v.plan_id
          )
        GROUP BY pe.exercise_id
        """,
    )
    fun observeExercisePlanUsage(): Flow<List<ExercisePlanUsageRow>>

    @Insert
    suspend fun insertAll(entities: List<PlannedExerciseEntity>)
}
