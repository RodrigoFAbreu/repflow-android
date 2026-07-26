package com.repflow.app.application.exercise

import com.repflow.app.application.common.Clock
import java.time.Instant

/** A [Clock] with a mutable, explicitly-advanced time, for deterministic tests. */
class FixedClock(
    initial: Instant,
) : Clock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun advanceTo(instant: Instant) {
        current = instant
    }
}
