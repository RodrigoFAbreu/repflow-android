package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for a training plan (schema v2, see
 * `docs/milestones/active/milestone-2-reference.md`).
 *
 * This type is a pure infrastructure detail - only
 * [com.repflow.app.data.trainingplan.TrainingPlanEntityMapper] and
 * [com.repflow.app.data.trainingplan.LocalTrainingPlanRepository] see it; no
 * domain, application or presentation code references it.
 *
 * `name` is the cleaned display form; `nameKey` is the lower-cased
 * identity/search form and carries the sole source of truth for uniqueness
 * via the `index_training_plans_name_key` unique index - mirrors
 * [com.repflow.app.infrastructure.database.exercise.ExerciseEntity] exactly.
 */
@Entity(
    tableName = "training_plans",
    indices = [
        Index(value = ["name_key"], unique = true, name = "index_training_plans_name_key"),
    ],
)
data class TrainingPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
