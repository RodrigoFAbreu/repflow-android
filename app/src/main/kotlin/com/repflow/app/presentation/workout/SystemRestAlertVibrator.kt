package com.repflow.app.presentation.workout

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [RestAlertVibrator]: one one-shot buzz through the system
 * vibrator (`VIBRATE` is declared in the manifest), mapping the usage back to
 * [VibrationAttributes.createForUsage] on API 33+ and to
 * [AudioAttributes.Builder.setUsage] on API 28-32. A thin pass-through to the
 * platform; the decision of whether to buzz, and with which usage, is
 * [RestTimerExpiryHandler]'s.
 */
class SystemRestAlertVibrator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RestAlertVibrator {
        @Suppress("DEPRECATION") // the AudioAttributes overload is the API 28-32 path only
        override fun vibrate(usage: Int) {
            val vibrator = systemVibrator() ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createOneShot(BUZZ_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(usage))
            } else {
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(usage).build())
            }
        }

        private fun systemVibrator(): Vibrator? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                context.getSystemService(Vibrator::class.java)
            }

        private companion object {
            const val BUZZ_MILLIS = 400L
        }
    }

/**
 * Binds [SystemRestAlertVibrator]. It lives in `presentation/workout/` beside
 * the receiver it serves: `infrastructure/di` may not import `presentation`
 * (`LayerBoundaryTest`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RestAlertModule {
    @Binds
    @Singleton
    abstract fun bindRestAlertVibrator(impl: SystemRestAlertVibrator): RestAlertVibrator
}
