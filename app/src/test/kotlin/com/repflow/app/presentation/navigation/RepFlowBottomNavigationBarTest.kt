package com.repflow.app.presentation.navigation

import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins what the bottom nav's own scoped overrides resolve to, and the
 * destination -> glyph wiring this checkpoint introduces.
 *
 * `RepFlowThemeTest` already proves the *values* clear their contrast floors
 * (`onSurface` at each theme's unselected alpha over `surfaceContainer`; each
 * theme's selected label over its composited pill). What it cannot see is
 * whether the bar actually reads them - swapping the two alphas, or applying
 * dark's `accent300` label in light, would leave every one of those assertions
 * green while shipping a 1.38:1 nav. These tests close that half: the render
 * inputs come from `repFlowNavColors`, and they are asserted against the same
 * named constants `RepFlowThemeTest` measures.
 *
 * The colours' actual *application* to `NavigationBarItem` stays a composable
 * concern, and so stays out of a plain-JVM suite; CP7's manual dark/light pass
 * covers it.
 */
class RepFlowBottomNavigationBarTest {
    @Test
    fun darkNavColorsReadTheDarkDesignValues() {
        val colors = repFlowNavColors(RepFlowDarkColorScheme)
        assertEquals(
            RepFlowDarkColorScheme.onSurface.copy(alpha = RepFlowColor.navUnselectedAlphaDark),
            colors.unselected,
        )
        assertEquals(
            RepFlowDarkColorScheme.primary.copy(alpha = RepFlowColor.navSelectedIndicatorAlphaDark),
            colors.selectedIndicator,
        )
        assertEquals(RepFlowColor.accent300, colors.selectedIcon)
    }

    @Test
    fun lightNavColorsReadTheLightDesignValues() {
        val colors = repFlowNavColors(RepFlowLightColorScheme)
        assertEquals(
            RepFlowLightColorScheme.onSurface.copy(alpha = RepFlowColor.navUnselectedAlphaLight),
            colors.unselected,
        )
        assertEquals(
            RepFlowLightColorScheme.primary.copy(alpha = RepFlowColor.navSelectedIndicatorAlphaLight),
            colors.selectedIndicator,
        )
        assertEquals(RepFlowLightColorScheme.primary, colors.selectedIcon)
    }

    /**
     * The light theme must not inherit dark's pair: `accent300` on the light
     * bar measures 1.38:1, and the two unselected alphas differ on purpose
     * (the design's literal .55 fails AA in light, so light carries .66).
     */
    @Test
    fun eachThemeGetsItsOwnPairRatherThanOneSharedSet() {
        val dark = repFlowNavColors(RepFlowDarkColorScheme)
        val light = repFlowNavColors(RepFlowLightColorScheme)
        assertNotEquals(dark.selectedIcon, light.selectedIcon)
        assertNotEquals(dark.unselected.alpha, light.unselected.alpha)
        assertNotEquals(dark.selectedIndicator.alpha, light.selectedIndicator.alpha)
    }

    @Test
    fun everyTopLevelDestinationCarriesItsOwnNavGlyph() {
        val expected =
            mapOf(
                RepFlowDestinations.EXERCISES to RepFlowIcons.Nav.exercises,
                RepFlowDestinations.WORKOUT to RepFlowIcons.Nav.workout,
                RepFlowDestinations.PLANS to RepFlowIcons.Nav.plans,
                RepFlowDestinations.RECOVERY to RepFlowIcons.Nav.recovery,
                RepFlowDestinations.HISTORY to RepFlowIcons.Nav.history,
                RepFlowDestinations.BACKUP to RepFlowIcons.Nav.backup,
            )
        assertEquals(
            expected,
            RepFlowDestinations.TOP_LEVEL_DESTINATIONS.associate { it.route to it.icon },
        )
    }

    /** This checkpoint is visual only: same six routes, same order. */
    @Test
    fun topLevelDestinationsKeepTheirRoutesAndOrder() {
        assertEquals(
            listOf(
                RepFlowDestinations.EXERCISES,
                RepFlowDestinations.WORKOUT,
                RepFlowDestinations.PLANS,
                RepFlowDestinations.RECOVERY,
                RepFlowDestinations.HISTORY,
                RepFlowDestinations.BACKUP,
            ),
            RepFlowDestinations.TOP_LEVEL_DESTINATIONS.map { it.route },
        )
    }
}
