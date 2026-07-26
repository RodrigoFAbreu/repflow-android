package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for one immutable version of a training
 * plan (schema v2). Every planned exercise for this version lives in a
 * [PlannedExerciseEntity] row referencing this row's [id].
 *
 * Rows here are never updated once inserted (see
 * [com.repflow.app.data.trainingplan.LocalTrainingPlanRepository.addVersion]) -
 * revising a plan always inserts a brand-new row with an incremented
 * [versionNumber], never mutating an existing one.
 *
 * The `ON DELETE CASCADE` foreign key to `training_plans` documents intent
 * for a future plan-delete operation (none exists yet in the MVP), matching
 * how `exercises` already anticipates future needs.
 */
@Entity(
    tableName = "training_plan_versions",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["plan_id", "version_number"], unique = true, name = "index_training_plan_versions_plan_version"),
        Index(value = ["plan_id"], name = "index_training_plan_versions_plan_id"),
    ],
)
data class TrainingPlanVersionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "version_number") val versionNumber: Int,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
