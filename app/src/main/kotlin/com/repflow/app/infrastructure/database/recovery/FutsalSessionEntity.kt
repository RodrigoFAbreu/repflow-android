package com.repflow.app.infrastructure.database.recovery

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for a futsal session (schema v5), mirroring
 * [RecoveryEntryEntity]. At most one row exists per [entryDate], enforced by
 * `index_futsal_sessions_entry_date`.
 */
@Entity(
    tableName = "futsal_sessions",
    indices = [
        Index(value = ["entry_date"], unique = true, name = "index_futsal_sessions_entry_date"),
    ],
)
data class FutsalSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_date") val entryDate: String,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int,
    @ColumnInfo(name = "session_rpe") val sessionRpe: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
