package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity

/**
 * The Room-persisted row shape for one exercise configured inside a
 * [TrainingPlanVersionEntity], in order (schema v2).
 *
 * `target_kind` only ever stores `"REPS"` or `"DURATION"` (the two
 * `PlannedExerciseTarget` cases) - a different, smaller vocabulary than
 * `ExerciseTrackingType`'s three stable names; do not conflate them.
 * `rep_min`/`rep_max` are populated only for `"REPS"`, `duration_min_seconds`/
 * `duration_max_seconds` only for `"DURATION"`; exactly one pair is non-null
 * per row, mirroring the domain's [com.repflow.app.domain.trainingplan.PlannedExerciseTarget]
 * sealed shape.
 *
 * The foreign key to [ExerciseEntity] uses the default `NO ACTION` behavior -
 * exercises are never hard-deleted in the MVP, so no cascade is needed there.
 */
@Entity(
    tableName = "planned_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["version_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
    ],
    indices = [
        Index(value = ["version_id", "sort_order"], unique = true, name = "index_planned_exercises_version_order"),
        Index(value = ["version_id"], name = "index_planned_exercises_version_id"),
        Index(value = ["exercise_id"], name = "index_planned_exercises_exercise_id"),
    ],
)
data class PlannedExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "version_id") val versionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "target_sets") val targetSets: Int,
    @ColumnInfo(name = "target_kind") val targetKind: String,
    @ColumnInfo(name = "rep_min") val repMin: Int?,
    @ColumnInfo(name = "rep_max") val repMax: Int?,
    @ColumnInfo(name = "duration_min_seconds") val durationMinSeconds: Long?,
    @ColumnInfo(name = "duration_max_seconds") val durationMaxSeconds: Long?,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Long?,
    @ColumnInfo(name = "is_optional") val isOptional: Boolean,
    @ColumnInfo(name = "target_warmup_sets") val targetWarmupSets: Int? = null,
)
