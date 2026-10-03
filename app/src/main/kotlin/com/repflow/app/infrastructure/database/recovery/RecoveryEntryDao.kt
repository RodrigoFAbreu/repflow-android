package com.repflow.app.infrastructure.database.recovery

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data access for [RecoveryEntryEntity]. */
@Dao
interface RecoveryEntryDao {
    @Query("SELECT * FROM recovery_entries WHERE entry_date = :entryDate")
    suspend fun findForDate(entryDate: String): RecoveryEntryEntity?

    /** [findForDate] as a Room `Flow`: re-emits on every write to the table. */
    @Query("SELECT * FROM recovery_entries WHERE entry_date = :entryDate")
    fun observeForDate(entryDate: String): Flow<RecoveryEntryEntity?>

    @Query("SELECT * FROM recovery_entries ORDER BY entry_date DESC LIMIT 1")
    suspend fun findLatest(): RecoveryEntryEntity?

    /** Every recovery entry, most recent first, for backup export. */
    @Query("SELECT * FROM recovery_entries ORDER BY entry_date DESC")
    suspend fun findAll(): List<RecoveryEntryEntity>

    /** Replaces any existing row for [RecoveryEntryEntity.entryDate] (see the entity's unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecoveryEntryEntity)
}
