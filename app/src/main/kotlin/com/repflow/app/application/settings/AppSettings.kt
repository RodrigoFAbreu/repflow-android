package com.repflow.app.application.settings

import java.time.Instant

/**
 * The device's preferences (remediation-1 CP14), as Settings shows them and
 * as the behaviours they gate read them.
 *
 * Device settings, not training data: they are not in the backup snapshot,
 * a restore applies the receiving device's own values, and `Erase all data`
 * leaves them as they are.
 *
 * @property restTimerAutoStart a rest timer starts after every logged set; off
 *   means no rest timer for logged sets at all (nothing else starts one).
 * @property restTimerVibrate the phone buzzes when a rest ends.
 * @property restTimerNotification a notification is posted when a rest ends.
 * @property keepScreenAwake the screen stays on while the workout surface shows.
 * @property confirmBeforeFinishing every finish request raises the finish sheet
 *   rather than finishing at once.
 * @property theme the colour scheme preference.
 * @property defaultRestSeconds the app-wide default rest after a set, in whole
 *   seconds (1-1800).
 * @property extraSetFields how focus mode shows RPE, pain and technique.
 * @property lastBackupAt when a backup export last succeeded on this device, or
 *   `null` before the first.
 */
data class AppSettings(
    val restTimerAutoStart: Boolean,
    val restTimerVibrate: Boolean,
    val restTimerNotification: Boolean,
    val keepScreenAwake: Boolean,
    val confirmBeforeFinishing: Boolean,
    val theme: ThemeMode = ThemeMode.DEFAULT,
    val defaultRestSeconds: Int = DEFAULT_REST_SECONDS,
    val extraSetFields: ExtraSetFields = ExtraSetFields.DEFAULT,
    val lastBackupAt: Instant? = null,
) {
    companion object {
        /** The initial app-wide default rest: 1:30, so behaviour is unchanged on upgrade. */
        const val DEFAULT_REST_SECONDS = 90

        /**
         * The values that keep the app's behaviour from before Settings existed:
         * rest auto-start, vibrate and notification on, keep screen awake off
         * (register `D21`), confirm before finishing on. Also what a read
         * returns if the stored row is ever absent.
         */
        val DEFAULT =
            AppSettings(
                restTimerAutoStart = true,
                restTimerVibrate = true,
                restTimerNotification = true,
                keepScreenAwake = false,
                confirmBeforeFinishing = true,
                theme = ThemeMode.DEFAULT,
                defaultRestSeconds = DEFAULT_REST_SECONDS,
                extraSetFields = ExtraSetFields.DEFAULT,
                lastBackupAt = null,
            )
    }
}
