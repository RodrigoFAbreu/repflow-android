package com.repflow.app.infrastructure.database.workout

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity

/**
 * The Room-persisted row shape for a [com.repflow.app.domain.workout.WorkoutSession]
 * (schema v3).
 *
 * `training_plan_version_id` is nullable (ad hoc sessions) and uses the
 * default `NO ACTION` foreign-key behavior - plan versions are never
 * deleted in the MVP, and a session must keep referencing the exact version
 * it was started from even if that plan is later revised (see the domain
 * model's "training-plan edits do not mutate completed workout history"
 * invariant).
 */
@Entity(
    tableName = "workout_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TrainingPlanVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["training_plan_version_id"],
        ),
    ],
    indices = [
        Index(value = ["training_plan_version_id"], name = "index_workout_sessions_training_plan_version_id"),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "training_plan_version_id") val trainingPlanVersionId: String?,
    val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "rest_timer_end_at_epoch_ms") val restTimerEndAtEpochMs: Long?,
    @ColumnInfo(name = "rest_timer_total_duration_seconds") val restTimerTotalDurationSeconds: Int?,
    @ColumnInfo(name = "invalidated_at") val invalidatedAt: Long? = null,
)
