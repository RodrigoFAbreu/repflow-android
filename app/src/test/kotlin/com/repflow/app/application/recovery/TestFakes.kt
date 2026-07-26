package com.repflow.app.application.recovery

import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import java.time.Instant

/** A [Clock] with a mutable, explicitly-advanced time, mirroring the exercise test fake. */
class FixedClock(
    initial: Instant,
) : Clock {
    private var current: Instant = initial

    override fun now(): Instant = current

    fun advanceTo(instant: Instant) {
        current = instant
    }
}

/** Produces predictable, sequential ids, mirroring the exercise test fake. */
class SequentialIdentifierGenerator(
    private val prefix: String = "recovery",
) : IdentifierGenerator {
    private var counter = 0

    override fun newId(): String {
        counter += 1
        return "$prefix-$counter"
    }
}
