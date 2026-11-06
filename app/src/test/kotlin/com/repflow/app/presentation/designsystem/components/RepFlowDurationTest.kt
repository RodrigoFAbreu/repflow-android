package com.repflow.app.presentation.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RepFlowDurationTest {
    @Test
    fun secondsReadAsTheClockWithTwoDigitSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:05", formatDuration(5))
        assertEquals("0:45", formatDuration(45))
        assertEquals("1:00", formatDuration(60))
        assertEquals("1:30", formatDuration(90))
        assertEquals("15:00", formatDuration(900))
    }

    @Test
    fun minutesAreNotCapped() {
        assertEquals("120:00", formatDuration(7200))
        assertEquals("16666:39", formatDuration(999999))
    }
}
