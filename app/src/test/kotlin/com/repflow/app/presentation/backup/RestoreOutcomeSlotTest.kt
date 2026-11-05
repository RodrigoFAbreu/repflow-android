package com.repflow.app.presentation.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

class RestoreOutcomeSlotTest {
    private val slot = RestoreOutcomeSlot()

    /** Two instances would compile and pass any test that builds its own, and carry nothing between screens. */
    @Test
    fun `the class is a singleton, so a reopened screen sees what the old one reported`() {
        assertTrue(RestoreOutcomeSlot::class.java.isAnnotationPresent(Singleton::class.java))
    }

    @Test
    fun `a reported message stays until it is consumed`() {
        slot.report(BackupStatusMessage.OperationFailed)
        assertEquals(BackupStatusMessage.OperationFailed, slot.outcome.value)

        slot.consume(BackupStatusMessage.OperationFailed)
        assertNull(slot.outcome.value)
    }

    @Test
    fun `a newer message survives an older one's consume`() {
        slot.report(BackupStatusMessage.OperationFailed)
        slot.report(BackupStatusMessage.RestoreSucceeded)

        slot.consume(BackupStatusMessage.OperationFailed)

        assertEquals(BackupStatusMessage.RestoreSucceeded, slot.outcome.value)
    }
}
