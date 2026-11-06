package com.repflow.app.infrastructure.database

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Whether any training-data table holds a row. The `settings` table isn't one. */
private const val HAS_ANY_ROW_SQL = """
    SELECT EXISTS(SELECT 1 FROM workout_sets)
        OR EXISTS(SELECT 1 FROM workout_exercises)
        OR EXISTS(SELECT 1 FROM workout_sessions)
        OR EXISTS(SELECT 1 FROM planned_exercises)
        OR EXISTS(SELECT 1 FROM training_plan_versions)
        OR EXISTS(SELECT 1 FROM training_plans)
        OR EXISTS(SELECT 1 FROM exercises)
        OR EXISTS(SELECT 1 FROM recovery_entries)
        OR EXISTS(SELECT 1 FROM futsal_sessions)
        OR EXISTS(SELECT 1 FROM progression_recommendations)
"""

/**
 * Deletes for every training-data table - the ten entity types the backup
 * snapshot carries - and for nothing else (remediation-1 CP14), and the one
 * question of whether any of them holds a row. Children
 * before parents, so no foreign key is ever left pointing at a deleted row
 * part-way through: sets, then workout exercises, then sessions (which
 * reference plan versions), then planned exercises, plan versions and plans,
 * then exercises; recovery, futsal and recommendation rows reference nothing.
 *
 * The `settings` table is deliberately absent: preferences are device
 * settings, not training data. Called only through
 * [com.repflow.app.data.backup.LocalTrainingDataRepository], which wraps the
 * whole sequence in one transaction.
 */
@Dao
@Suppress("TooManyFunctions") // One delete per training-data table, by design, plus the one question over all of them, asked two ways.
interface TrainingDataDao {
    @Query(HAS_ANY_ROW_SQL)
    suspend fun hasAnyRow(): Boolean

    /** [hasAnyRow], again after each commit that touches one of these tables. Room reads the tables from the query. */
    @Query(HAS_ANY_ROW_SQL)
    fun observeHasAnyRow(): Flow<Boolean>

    @Query("DELETE FROM workout_sets")
    suspend fun deleteWorkoutSets()

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteWorkoutExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteWorkoutSessions()

    @Query("DELETE FROM planned_exercises")
    suspend fun deletePlannedExercises()

    @Query("DELETE FROM training_plan_versions")
    suspend fun deleteTrainingPlanVersions()

    @Query("DELETE FROM training_plans")
    suspend fun deleteTrainingPlans()

    @Query("DELETE FROM exercises")
    suspend fun deleteExercises()

    @Query("DELETE FROM recovery_entries")
    suspend fun deleteRecoveryEntries()

    @Query("DELETE FROM futsal_sessions")
    suspend fun deleteFutsalSessions()

    @Query("DELETE FROM progression_recommendations")
    suspend fun deleteProgressionRecommendations()
}
