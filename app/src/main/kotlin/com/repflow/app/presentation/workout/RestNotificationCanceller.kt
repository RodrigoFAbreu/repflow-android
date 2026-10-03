package com.repflow.app.presentation.workout

import android.app.NotificationManager
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clears the "Rest done" notification once the workout it was for has gone
 * (finished, abandoned, erased or replaced by a restore). The seam lets the
 * ViewModels that end a session do so without touching the platform.
 */
fun interface RestNotificationCanceller {
    fun cancel()
}

/**
 * The production [RestNotificationCanceller]: cancels notification
 * [RestTimerExpiredReceiver.NOTIFICATION_ID] only. The `rest_timer_v2` channel is
 * left in place, since it carries the user's own sound and importance choices.
 */
class SystemRestNotificationCanceller
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RestNotificationCanceller {
        override fun cancel() {
            context
                .getSystemService(NotificationManager::class.java)
                ?.cancel(RestTimerExpiredReceiver.NOTIFICATION_ID)
        }
    }

/** Binds [SystemRestNotificationCanceller], beside [RestAlertModule]'s pattern. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RestNotificationModule {
    @Binds
    @Singleton
    abstract fun bindRestNotificationCanceller(impl: SystemRestNotificationCanceller): RestNotificationCanceller
}
