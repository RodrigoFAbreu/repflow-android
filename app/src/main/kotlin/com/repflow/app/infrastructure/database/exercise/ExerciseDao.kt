package com.repflow.app.infrastructure.database.exercise

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room data access for [ExerciseEntity]. No delete method exists in
 * Milestone 1 (see plan.md D-12 / additional implementation correction 13).
 *
 * [observe]'s `likePattern` is expected to already be a fully-built,
 * escaped `LIKE` pattern (see [com.repflow.app.data.exercise.buildNameSearchPattern]) -
 * the search input is never interpolated into SQL here (see plan.md
 * additional implementation correction 5).
 */
@Dao
interface ExerciseDao {
    @Query(
        """
        SELECT * FROM exercises
        WHERE (
            (:archived = 0 AND archived_at IS NULL) OR (:archived = 1 AND archived_at IS NOT NULL)
        )
        AND name_key LIKE :likePattern ESCAPE '\'
        ORDER BY name_key ASC, id ASC
        """,
    )
    fun observe(
        archived: Boolean,
        likePattern: String,
    ): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun findById(id: String): ExerciseEntity?

    @Query("SELECT id FROM exercises WHERE name_key = :nameKey")
    suspend fun findIdByNameKey(nameKey: String): String?

    /**
     * Insert semantics only (see plan.md additional implementation
     * correction 3) - the default `OnConflictStrategy.ABORT` throws on a
     * primary-key or `name_key` unique-index conflict, which the repository
     * translates.
     */
    @Insert
    suspend fun insert(entity: ExerciseEntity)

    /**
     * Update semantics only, never an upsert (see plan.md additional
     * implementation correction 4). Returns the number of rows changed so
     * the repository can detect a row missing at write time (0 rows).
     */
    @Update
    suspend fun update(entity: ExerciseEntity): Int
}
