package com.repflow.app.presentation

import com.repflow.app.application.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSelectionTest {
    @Test
    fun systemFollowsTheDeviceWhileLightAndDarkOverrideIt() {
        assertEquals(false, isDarkTheme(ThemeMode.SYSTEM, systemDark = false))
        assertEquals(true, isDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, isDarkTheme(ThemeMode.LIGHT, systemDark = false))
        assertEquals(false, isDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, isDarkTheme(ThemeMode.DARK, systemDark = false))
        assertEquals(true, isDarkTheme(ThemeMode.DARK, systemDark = true))
    }
}
