package com.repflow.app.infrastructure.database.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room data access for [FutsalSessionEntity], mirroring [RecoveryEntryDao]. */
@Dao
interface FutsalSessionDao {
    @Query("SELECT * FROM futsal_sessions WHERE entry_date = :entryDate")
    suspend fun findForDate(entryDate: String): FutsalSessionEntity?

    @Query("SELECT * FROM futsal_sessions WHERE entry_date >= :sinceDate ORDER BY entry_date DESC")
    suspend fun findSince(sinceDate: String): List<FutsalSessionEntity>

    /** Replaces any existing row for [FutsalSessionEntity.entryDate] (see the entity's unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FutsalSessionEntity)
}
