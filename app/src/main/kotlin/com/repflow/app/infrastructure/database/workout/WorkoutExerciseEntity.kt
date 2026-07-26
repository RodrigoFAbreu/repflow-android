package com.repflow.app.infrastructure.database.workout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity

/**
 * The Room-persisted row shape for a [com.repflow.app.domain.workout.WorkoutExercise]
 * (schema v3).
 *
 * `exercise_name_snapshot` and `tracking_type` are copied at creation time
 * rather than joined live from `exercises`, matching the domain type's
 * historical-meaning rationale. `planned_exercise_id` is null for an ad hoc
 * exercise.
 */
@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
        ),
        ForeignKey(
            entity = PlannedExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["planned_exercise_id"],
        ),
    ],
    indices = [
        Index(value = ["session_id", "sort_order"], unique = true, name = "index_workout_exercises_session_order"),
        Index(value = ["session_id"], name = "index_workout_exercises_session_id"),
        Index(value = ["exercise_id"], name = "index_workout_exercises_exercise_id"),
        Index(value = ["planned_exercise_id"], name = "index_workout_exercises_planned_exercise_id"),
    ],
)
data class WorkoutExerciseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "exercise_name_snapshot") val exerciseNameSnapshot: String,
    @ColumnInfo(name = "tracking_type") val trackingType: String,
    @ColumnInfo(name = "planned_exercise_id") val plannedExerciseId: String?,
)
