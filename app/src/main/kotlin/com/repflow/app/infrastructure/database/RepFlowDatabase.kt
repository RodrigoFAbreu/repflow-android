package com.repflow.app.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseDao
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanEntity
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionEntity

/**
 * RepFlow's single Room database. Version 2 adds the training-plan tables
 * via a real, additive [MIGRATION_1_2] - there is deliberately no
 * `fallbackToDestructiveMigration` anywhere in this codebase (see plan.md
 * section G and additional implementation correction 14).
 */
@Database(
    entities = [
        ExerciseEntity::class,
        TrainingPlanEntity::class,
        TrainingPlanVersionEntity::class,
        PlannedExerciseEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class RepFlowDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun trainingPlanDao(): TrainingPlanDao

    abstract fun trainingPlanVersionDao(): TrainingPlanVersionDao

    abstract fun plannedExerciseDao(): PlannedExerciseDao
}
