package com.repflow.app.presentation.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a confirmed restore is running, for [RestoringOverlayHost] to block the whole app. It counts,
 * so that if two restores ever overlapped the first to end could not lift the second's screen.
 *
 * `@Singleton` is what makes this work, as for [RestoreOutcomeSlot]: a restore outlives its screen's
 * ViewModel, and two instances would compile and pass any test that builds its own, which is why
 * `RestoreInProgressTest` asserts the annotation.
 */
@Singleton
class RestoreInProgress
    @Inject
    constructor() {
        private var running = 0
        private val flag = MutableStateFlow(false)

        val active: StateFlow<Boolean> = flag.asStateFlow()

        /** Synchronized because the count and the flag are two values that have to change as one. */
        @Synchronized
        fun begin() {
            running++
            flag.value = true
        }

        @Synchronized
        fun end() {
            running--
            flag.value = running > 0
        }
    }
