package com.repflow.app.application.common

import java.time.Instant

/**
 * A testable indirection over wall-clock time, so use cases never call
 * [Instant.now] directly and tests can supply deterministic timestamps.
 */
fun interface Clock {
    fun now(): Instant
}
