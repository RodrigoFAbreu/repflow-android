package com.repflow.app.infrastructure.database.workout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The Room-persisted row shape for a [com.repflow.app.domain.workout.WorkoutSet]
 * (schema v3).
 *
 * Which of `load`, `reps`, `duration_seconds` are populated mirrors the
 * owning [WorkoutExerciseEntity.trackingType] and is validated on the
 * domain side ([com.repflow.app.domain.workout.WorkoutSet.create]), not
 * re-validated here.
 */
@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workout_exercise_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workout_exercise_id", "sort_order"], unique = true, name = "index_workout_sets_exercise_order"),
        Index(value = ["workout_exercise_id"], name = "index_workout_sets_exercise_id"),
    ],
)
data class WorkoutSetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val load: Double?,
    val reps: Int?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    val rpe: Double?,
    @ColumnInfo(name = "is_warmup") val isWarmup: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
