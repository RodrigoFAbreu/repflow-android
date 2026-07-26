package com.repflow.app.infrastructure.database.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Room data access for [RecoveryEntryEntity]. */
@Dao
interface RecoveryEntryDao {
    @Query("SELECT * FROM recovery_entries WHERE entry_date = :entryDate")
    suspend fun findForDate(entryDate: String): RecoveryEntryEntity?

    @Query("SELECT * FROM recovery_entries ORDER BY entry_date DESC LIMIT 1")
    suspend fun findLatest(): RecoveryEntryEntity?

    /** Replaces any existing row for [RecoveryEntryEntity.entryDate] (see the entity's unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecoveryEntryEntity)
}
