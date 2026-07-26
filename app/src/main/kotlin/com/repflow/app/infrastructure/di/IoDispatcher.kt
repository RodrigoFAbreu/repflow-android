package com.repflow.app.infrastructure.di

import javax.inject.Qualifier

/**
 * Qualifies the background [kotlinx.coroutines.CoroutineDispatcher] used for
 * disk/database work, so it can be swapped for a test dispatcher without
 * touching call sites (see [SystemModule]).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
