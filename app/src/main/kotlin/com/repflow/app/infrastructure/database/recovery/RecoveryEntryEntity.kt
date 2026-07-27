package com.repflow.app.infrastructure.database.recovery

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for a recovery entry (schema v5, see
 * `docs/milestones/completed/milestone-5-reference.md`). At most one row
 * exists per [entryDate], enforced by `index_recovery_entries_entry_date`.
 *
 * This type is a pure infrastructure detail - only
 * [com.repflow.app.data.recovery.RecoveryEntryMapper] and
 * [com.repflow.app.data.recovery.LocalRecoveryRepository] see it.
 */
@Entity(
    tableName = "recovery_entries",
    indices = [
        Index(value = ["entry_date"], unique = true, name = "index_recovery_entries_entry_date"),
    ],
)
data class RecoveryEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entry_date") val entryDate: String,
    @ColumnInfo(name = "sleep_quality") val sleepQuality: Int,
    val energy: Int,
    @ColumnInfo(name = "leg_doms") val legDoms: Int,
    @ColumnInfo(name = "heel_stiffness") val heelStiffness: Int,
    @ColumnInfo(name = "pain_while_walking") val painWhileWalking: Int,
    @ColumnInfo(name = "heavy_legs") val heavyLegs: Int,
    @ColumnInfo(name = "futsal_in_previous_24h") val futsalInPrevious24h: Boolean,
    @ColumnInfo(name = "futsal_expected_next_24h") val futsalExpectedNext24h: Boolean,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
