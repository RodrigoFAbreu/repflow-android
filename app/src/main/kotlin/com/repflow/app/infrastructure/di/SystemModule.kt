package com.repflow.app.infrastructure.di

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.infrastructure.id.UuidIdentifierGenerator
import com.repflow.app.infrastructure.time.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/** Binds the production [Clock] and [IdentifierGenerator], and provides the IO dispatcher. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SystemModule {
    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindIdentifierGenerator(impl: UuidIdentifierGenerator): IdentifierGenerator

    companion object {
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    }
}
