package com.repflow.app.presentation.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.repflow.app.R

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_DIGITS = 2

/**
 * A whole number of seconds as the clock the design reads (`A14`: "Durations
 * are m:ss"): `0:45`, `1:30`, `15:00`. Minutes are never capped, so the plan
 * range's top is `120:00` and a long stored value is `16666:39`.
 */
fun formatDuration(seconds: Long): String {
    val minutes = seconds / SECONDS_PER_MINUTE
    val remainder = (seconds % SECONDS_PER_MINUTE).toString().padStart(SECONDS_DIGITS, '0')
    return "$minutes:$remainder"
}

/**
 * [formatDuration] as a screen reader should say it: `1 minute 30 seconds`,
 * `15 minutes`, `45 seconds`. Google's speech engine reads a bare clock as a time
 * of day or a number, so `2:00` is "two o'clock" and `15:00` is "fifteen hundred".
 * The values a person changes, on the stepper and the pad, carry this; read-only
 * text keeps the clock.
 */
@Composable
fun spokenDuration(seconds: Long): String {
    val minutes = (seconds / SECONDS_PER_MINUTE).toInt()
    val remainder = (seconds % SECONDS_PER_MINUTE).toInt()
    val minutesText = pluralStringResource(R.plurals.repflow_duration_spoken_minutes, minutes, minutes)
    val secondsText = pluralStringResource(R.plurals.repflow_duration_spoken_seconds, remainder, remainder)
    return when {
        minutes == 0 -> secondsText
        remainder == 0 -> minutesText
        else -> "$minutesText $secondsText"
    }
}
