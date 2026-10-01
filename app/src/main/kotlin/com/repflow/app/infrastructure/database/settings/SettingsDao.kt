package com.repflow.app.infrastructure.database.settings

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room data access for the single [SettingsEntity] row. */
@Dao
interface SettingsDao {
    /** The pinned row, or `null` if it is absent. */
    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = ${SettingsEntity.SINGLETON_ID}")
    suspend fun find(): SettingsEntity?

    /** Writes the whole row, creating it if it is absent. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)
}
