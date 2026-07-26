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
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseDao
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSessionDao
import com.repflow.app.infrastructure.database.workout.WorkoutSessionEntity
import com.repflow.app.infrastructure.database.workout.WorkoutSetDao
import com.repflow.app.infrastructure.database.workout.WorkoutSetEntity

/**
 * RepFlow's single Room database. Version 2 added the training-plan tables
 * via [MIGRATION_1_2]; version 3 adds the active-workout tables via
 * [MIGRATION_2_3]; version 4 adds the rest-timer columns via
 * [MIGRATION_3_4]. There is deliberately no
 * `fallbackToDestructiveMigration` anywhere in this codebase (see plan.md
 * section G and additional implementation correction 14).
 */
@Database(
    entities = [
        ExerciseEntity::class,
        TrainingPlanEntity::class,
        TrainingPlanVersionEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class RepFlowDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao

    abstract fun trainingPlanDao(): TrainingPlanDao

    abstract fun trainingPlanVersionDao(): TrainingPlanVersionDao

    abstract fun plannedExerciseDao(): PlannedExerciseDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    abstract fun workoutExerciseDao(): WorkoutExerciseDao

    abstract fun workoutSetDao(): WorkoutSetDao
}
