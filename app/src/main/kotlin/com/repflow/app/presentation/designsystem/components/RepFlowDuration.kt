package com.repflow.app.presentation.designsystem.components

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
