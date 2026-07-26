package com.repflow.app.infrastructure.di

import com.repflow.app.application.backup.BackupRepository
import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.application.progression.ProgressionRecommendationRepository
import com.repflow.app.application.recovery.FutsalRepository
import com.repflow.app.application.recovery.RecoveryRepository
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.application.workout.WorkoutRepository
import com.repflow.app.data.backup.LocalBackupRepository
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.progression.LocalProgressionRecommendationRepository
import com.repflow.app.data.recovery.LocalFutsalRepository
import com.repflow.app.data.recovery.LocalRecoveryRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds application-layer repository contracts to their data-layer implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: LocalExerciseRepository): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindTrainingPlanRepository(impl: LocalTrainingPlanRepository): TrainingPlanRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: LocalWorkoutRepository): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindRecoveryRepository(impl: LocalRecoveryRepository): RecoveryRepository

    @Binds
    @Singleton
    abstract fun bindFutsalRepository(impl: LocalFutsalRepository): FutsalRepository

    @Binds
    @Singleton
    abstract fun bindProgressionRecommendationRepository(
        impl: LocalProgressionRecommendationRepository,
    ): ProgressionRecommendationRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: LocalBackupRepository): BackupRepository
}
