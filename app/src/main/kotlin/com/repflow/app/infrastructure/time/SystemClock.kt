package com.repflow.app.infrastructure.time

import com.repflow.app.application.common.Clock
import java.time.Instant
import javax.inject.Inject

/** The production [Clock], backed by the platform's wall-clock time. */
class SystemClock
    @Inject
    constructor() : Clock {
        override fun now(): Instant = Instant.now()
    }
