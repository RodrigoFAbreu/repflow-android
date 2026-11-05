package com.repflow.app.presentation.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a restore's outcome to whichever Backup screen is there to show it. A confirmed restore
 * outlives its screen, and each screen has its own [BackupViewModel], so without this the message
 * would stop at a cleared ViewModel and a failure would go unseen. It holds one message: if two ever
 * met, the newer describes the data the user now has.
 *
 * `@Singleton` is what makes this work: two instances would compile and pass any test that builds its
 * own, which is why `RestoreOutcomeSlotTest` asserts the annotation.
 */
@Singleton
class RestoreOutcomeSlot
    @Inject
    constructor() {
        private val slot = MutableStateFlow<BackupStatusMessage?>(null)

        val outcome: StateFlow<BackupStatusMessage?> = slot.asStateFlow()

        fun report(message: BackupStatusMessage) {
            slot.value = message
        }

        /** Empties the slot only if it still holds [message], so a newer outcome survives an older one's consume. */
        fun consume(message: BackupStatusMessage) {
            slot.compareAndSet(message, null)
        }
    }
