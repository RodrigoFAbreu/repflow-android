package com.repflow.app.infrastructure.di

import android.content.Context
import androidx.room.Room
import com.repflow.app.infrastructure.database.MIGRATION_1_2
import com.repflow.app.infrastructure.database.MIGRATION_2_3
import com.repflow.app.infrastructure.database.MIGRATION_3_4
import com.repflow.app.infrastructure.database.MIGRATION_4_5
import com.repflow.app.infrastructure.database.MIGRATION_5_6
import com.repflow.app.infrastructure.database.MIGRATION_6_7
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.infrastructure.database.exercise.ExerciseDao
import com.repflow.app.infrastructure.database.progression.ProgressionRecommendationDao
import com.repflow.app.infrastructure.database.recovery.FutsalSessionDao
import com.repflow.app.infrastructure.database.recovery.RecoveryEntryDao
import com.repflow.app.infrastructure.database.trainingplan.PlannedExerciseDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanDao
import com.repflow.app.infrastructure.database.trainingplan.TrainingPlanVersionDao
import com.repflow.app.infrastructure.database.workout.WorkoutExerciseDao
import com.repflow.app.infrastructure.database.workout.WorkoutSessionDao
import com.repflow.app.infrastructure.database.workout.WorkoutSetDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the single [RepFlowDatabase] instance and its DAOs. There is
 * deliberately no `fallbackToDestructiveMigration` call here (see plan.md
 * additional implementation correction 14 and section G).
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object DatabaseModule {
    private const val DATABASE_NAME = "repflow.db"

    @Provides
    @Singleton
    fun provideRepFlowDatabase(
        @ApplicationContext context: Context,
    ): RepFlowDatabase =
        Room
            .databaseBuilder(context, RepFlowDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides
    fun provideExerciseDao(database: RepFlowDatabase): ExerciseDao = database.exerciseDao()

    @Provides
    fun provideTrainingPlanDao(database: RepFlowDatabase): TrainingPlanDao = database.trainingPlanDao()

    @Provides
    fun provideTrainingPlanVersionDao(database: RepFlowDatabase): TrainingPlanVersionDao = database.trainingPlanVersionDao()

    @Provides
    fun providePlannedExerciseDao(database: RepFlowDatabase): PlannedExerciseDao = database.plannedExerciseDao()

    @Provides
    fun provideWorkoutSessionDao(database: RepFlowDatabase): WorkoutSessionDao = database.workoutSessionDao()

    @Provides
    fun provideWorkoutExerciseDao(database: RepFlowDatabase): WorkoutExerciseDao = database.workoutExerciseDao()

    @Provides
    fun provideWorkoutSetDao(database: RepFlowDatabase): WorkoutSetDao = database.workoutSetDao()

    @Provides
    fun provideRecoveryEntryDao(database: RepFlowDatabase): RecoveryEntryDao = database.recoveryEntryDao()

    @Provides
    fun provideFutsalSessionDao(database: RepFlowDatabase): FutsalSessionDao = database.futsalSessionDao()

    @Provides
    fun provideProgressionRecommendationDao(database: RepFlowDatabase): ProgressionRecommendationDao =
        database.progressionRecommendationDao()
}
