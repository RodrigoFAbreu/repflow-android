package com.repflow.app.presentation.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Singleton

class RestoreInProgressTest {
    private val restoreInProgress = RestoreInProgress()

    /** Two instances would compile and pass any test that builds its own, and the screen would never rise. */
    @Test
    fun `the class is a singleton, so the activity sees what the ViewModel began`() {
        assertTrue(RestoreInProgress::class.java.isAnnotationPresent(Singleton::class.java))
    }

    @Test
    fun `it starts inactive`() {
        assertFalse(restoreInProgress.active.value)
    }

    @Test
    fun `begin makes it active and end makes it inactive again`() {
        restoreInProgress.begin()
        assertTrue(restoreInProgress.active.value)

        restoreInProgress.end()
        assertFalse(restoreInProgress.active.value)
    }

    @Test
    fun `a second restore's end cannot lift the first's screen`() {
        restoreInProgress.begin()
        restoreInProgress.begin()

        restoreInProgress.end()
        assertTrue(restoreInProgress.active.value)

        restoreInProgress.end()
        assertFalse(restoreInProgress.active.value)
    }
}
