package com.repflow.app.infrastructure.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.exercise.ExerciseEntity
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationDao
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationEntity
import com.repflow.app.infrastructure.database.recovery.FutsalSessionDao
import com.repflow.app.infrastructure.database.recovery.FutsalSessionEntity
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryDao
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryEntity
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
 * [MIGRATION_3_4]; version 5 adds the recovery/futsal tables via
 * [MIGRATION_4_5]; version 6 adds the progression-recommendation table via
 * [MIGRATION_5_6]. There is deliberately no `fallbackToDestructiveMigration`
 * anywhere in this codebase (see plan.md section G and additional
 * implementation correction 14). Version 7 adds five nullable columns
 * (`workout_sets.pain`/`technique_quality`,
 * `planned_exercises.target_warmup_sets`,
 * `workout_sessions.invalidated_at`, `training_plans.archived_at`) via
 * [MIGRATION_6_7].
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
        RecoveryEntryEntity::class,
        FutsalSessionEntity::class,
        ProgressionRecommendationEntity::class,
    ],
    version = 7,
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

    abstract fun recoveryEntryDao(): RecoveryEntryDao

    abstract fun futsalSessionDao(): FutsalSessionDao

    abstract fun progressionRecommendationDao(): ProgressionRecommendationDao
}
