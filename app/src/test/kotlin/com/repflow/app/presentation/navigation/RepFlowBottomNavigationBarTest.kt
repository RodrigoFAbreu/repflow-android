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
 * destination -> glyph wiring (the four-tab set and its regular/fill pairs
 * since remediation-1 CP2).
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

    /**
     * Each tab carries the design's regular/fill pair (remediation-1 CP2): the
     * bar draws [TopLevelDestination.icon] unselected and
     * [TopLevelDestination.selectedIcon] selected.
     */
    @Test
    fun everyTopLevelDestinationCarriesItsOwnNavGlyphPair() {
        val expected =
            mapOf(
                RepFlowDestinations.HOME to (RepFlowIcons.Nav.home to RepFlowIcons.Nav.homeSelected),
                RepFlowDestinations.PLANS to (RepFlowIcons.Nav.plans to RepFlowIcons.Nav.plansSelected),
                RepFlowDestinations.HISTORY to (RepFlowIcons.Nav.history to RepFlowIcons.Nav.historySelected),
                RepFlowDestinations.PROGRESS to (RepFlowIcons.Nav.progress to RepFlowIcons.Nav.progressSelected),
            )
        assertEquals(
            expected,
            RepFlowDestinations.TOP_LEVEL_DESTINATIONS.associate { it.route to (it.icon to it.selectedIcon) },
        )
    }

    /**
     * The design's four-destination bar, in its order (`4a`'s `nTabs`): Home,
     * Plans, History, Progress. This retires the six-tab pin the parent
     * milestone kept: Exercises, Workout, Recovery and Backup are no longer
     * tabs, and are reached from Home and Settings instead.
     */
    @Test
    fun topLevelDestinationsAreTheDesignsFourTabsInOrder() {
        assertEquals(
            listOf(
                RepFlowDestinations.HOME,
                RepFlowDestinations.PLANS,
                RepFlowDestinations.HISTORY,
                RepFlowDestinations.PROGRESS,
            ),
            RepFlowDestinations.TOP_LEVEL_DESTINATIONS.map { it.route },
        )
    }
}
