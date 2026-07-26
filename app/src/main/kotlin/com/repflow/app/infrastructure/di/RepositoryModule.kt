package com.repflow.app.infrastructure.di

import com.repflow.app.application.exercise.ExerciseRepository
import com.repflow.app.application.trainingplan.TrainingPlanRepository
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
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
}
