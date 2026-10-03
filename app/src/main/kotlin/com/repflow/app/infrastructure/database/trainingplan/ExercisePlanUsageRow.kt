package com.repflow.app.infrastructure.database.trainingplan

import androidx.room.ColumnInfo

/** One row of [PlannedExerciseDao.observeExercisePlanUsage]: an exercise and how many current plans hold it. */
data class ExercisePlanUsageRow(
    @ColumnInfo(name = "exercise_id") val exerciseId: String,
    @ColumnInfo(name = "plan_count") val planCount: Int,
)
