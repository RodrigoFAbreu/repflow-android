package com.repflow.app.domain.workout

import java.time.Instant

/**
 * A running rest timer for the single [WorkoutSessionStatus.ACTIVE]
 * [WorkoutSession].
 *
 * Remaining time is always *derived* from [endAt] against the current
 * instant - it is never stored as a mutable countdown value - so the timer
 * can be reconstructed exactly after backgrounding or a full process
 * restart, per the "rest timers use an absolute end timestamp" invariant.
 *
 * [totalDurationSeconds] is informational only (e.g. to render a progress
 * indicator); adjusting [endAt] via [withAddedSeconds]/[withRemovedSeconds]
 * never retroactively changes it.
 */
data class RestTimer(
    val endAt: Instant,
    val totalDurationSeconds: Int,
) {
    init {
        require(totalDurationSeconds > 0) { "totalDurationSeconds must be positive" }
    }

    /** Remaining whole seconds at [now], clamped to zero (never negative). */
    fun remainingSeconds(now: Instant): Long = (endAt.epochSecond - now.epochSecond).coerceAtLeast(0)

    /** Whether [now] is at or past [endAt]. */
    fun isExpired(now: Instant): Boolean = !now.isBefore(endAt)

    /** Pushes [endAt] further out by [seconds]. */
    fun withAddedSeconds(seconds: Long): RestTimer = copy(endAt = endAt.plusSeconds(seconds))

    /**
     * Pulls [endAt] closer by [seconds], never before [now] (removing time from an
     * already-near-expiry timer simply expires it immediately rather than going negative).
     */
    fun withRemovedSeconds(
        seconds: Long,
        now: Instant,
    ): RestTimer {
        val shifted = endAt.minusSeconds(seconds)
        return copy(endAt = if (shifted.isBefore(now)) now else shifted)
    }

    companion object {
        /** Starts a fresh timer of [durationSeconds] from [now]. */
        fun start(
            durationSeconds: Int,
            now: Instant,
        ): RestTimer = RestTimer(endAt = now.plusSeconds(durationSeconds.toLong()), totalDurationSeconds = durationSeconds)
    }
}
