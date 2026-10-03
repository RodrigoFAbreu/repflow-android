package com.repflow.app.presentation.designsystem

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Pins the design tokens themselves.
 *
 * None of these roles had a prior implementation to regress against, and an
 * unassigned role silently keeps its Material 3 default rather than failing to
 * compile - so a transcription error in `RepFlowColor.kt` is invisible to
 * everything except an assertion like these.
 *
 * The contrast assertions pin a *floor*, not the value measured today, and
 * read all three of their inputs (base colour, alpha, background) from
 * production rather than restating them here: a later edit to either scheme,
 * or to a shared alpha constant, has to keep clearing the floor.
 */
class RepFlowThemeTest {
    // region ColorScheme roles

    @Test
    fun darkSchemeAssignsItsDesignRoles() {
        with(RepFlowDarkColorScheme) {
            assertEquals(Color(0xFF9184D9), primary)
            assertEquals(Color(0xFF161826), onPrimary)
            assertEquals(Color(0xFFE9E9ED), secondary)
            assertEquals(Color(0xFF161826), background)
            assertEquals(Color(0xFFE9E9ED), onBackground)
            assertEquals(Color(0xFF232532), surface)
            assertEquals(Color(0xFFE9E9ED), onSurface)
            assertEquals(Color(0xFF1B1D2B), surfaceContainer)
            assertEquals(Color(0xFF232532), surfaceContainerHigh)
            assertEquals(Color(0xFFEB827B), error)
        }
    }

    @Test
    fun darkSchemeReassignsTheEditorCardContainerToSurface() {
        assertEquals(RepFlowDarkColorScheme.surface, RepFlowDarkColorScheme.surfaceContainerHighest)
    }

    @Test
    fun lightSchemeAssignsItsDesignRoles() {
        with(RepFlowLightColorScheme) {
            assertEquals(Color(0xFF5D5294), primary)
            assertEquals(Color(0xFFF5F4FF), onPrimary)
            assertEquals(Color(0xFF292B31), secondary)
            assertEquals(Color(0xFFE4E7F5), background)
            assertEquals(Color(0xFF292B31), onBackground)
            assertEquals(Color(0xFFF3F5FE), surface)
            assertEquals(Color(0xFF292B31), onSurface)
            assertEquals(Color(0xFFF3F5FE), surfaceContainer)
            assertEquals(Color(0xFFF3F5FE), surfaceContainerHigh)
            assertEquals(Color(0xFFA74541), error)
        }
    }

    // endregion

    // region Tokens with no ColorScheme slot

    @Test
    fun extraColorsCarryTheirPerThemeValues() {
        assertEquals(Color(0xFF292B31), RepFlowDarkExtraColors.control)
        assertEquals(Color(0xFF3F424D), RepFlowDarkExtraColors.hairline)
        assertEquals(Color(0xFFB2B6CA), RepFlowLightExtraColors.control)
        assertEquals(Color(0xFFCFD3E5), RepFlowLightExtraColors.hairline)
    }

    @Test
    fun lightControlAndHairlineStayDistinct() {
        // These collapsed onto one value once already, which left control-filled
        // primitives with no visible border and went unnoticed.
        assertNotEquals(RepFlowLightExtraColors.control, RepFlowLightExtraColors.hairline)
        assertNotEquals(RepFlowDarkExtraColors.control, RepFlowDarkExtraColors.hairline)
    }

    @Test
    fun theExtrasAreSelectedByTheAppliedSchemeNotBySomeOtherSignal() {
        // RepFlowTheme used to resolve these from its own isSystemInDarkTheme()
        // call, one level above the MaterialTheme it applied - the single place
        // the design system asked the system theme twice, and the one case
        // isDarkColorScheme's KDoc promises cannot happen. Pinning the mapping
        // scheme -> extras is what makes that promise checkable: swapping the
        // branch, or reintroducing a system-theme read, breaks this.
        assertEquals(RepFlowDarkExtraColors, repFlowExtraColors(RepFlowDarkColorScheme))
        assertEquals(RepFlowLightExtraColors, repFlowExtraColors(RepFlowLightColorScheme))
    }

    @Test
    fun accentRampStepsResolveToTheDesignValues() {
        assertEquals(Color(0xFFD2CEFD), RepFlowColor.accent300)
        assertEquals(Color(0xFF796CBF), RepFlowColor.accent600)
        assertEquals(Color(0xFF5D5294), RepFlowColor.accent700)
        assertEquals(Color(0xFF2B2741), RepFlowColor.accent900)
    }

    @Test
    fun oneOffRadiiResolveToTheirDesignValues() {
        assertEquals(CornerSize(9.dp), RepFlowShapes.stepper.topStart)
        assertEquals(CornerSize(18.dp), RepFlowShapes.fab.topStart)
        assertEquals(CornerSize(999.dp), RepFlowShapes.pill.topStart)
    }

    // endregion

    // region Contrast floors

    @Test
    fun lightPrimaryButtonLabelClearsTheTextFloor() {
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowLightColorScheme.onPrimary,
            background = RepFlowLightColorScheme.primary,
        )
    }

    @Test
    fun lightErrorTextClearsTheTextFloorOnBothSurfacesItRendersOn() {
        // An isError OutlinedTextField's supporting line sits either on the bare
        // page ground or on the plan editor's row card.
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowLightColorScheme.error,
            background = RepFlowLightColorScheme.background,
        )
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowLightColorScheme.error,
            background = RepFlowLightColorScheme.surfaceContainerHighest,
        )
    }

    @Test
    fun darkPrimaryClearsTheTextFloorOnTheDialogContainer() {
        // Nine TextButton action labels across four dialogs, plus the date
        // picker's "today" label, all resolve to primary on this container.
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowDarkColorScheme.primary,
            background = RepFlowDarkColorScheme.surfaceContainerHigh,
        )
    }

    @Test
    fun darkPrimaryClearsTheTextFloorOnTheEditorCardContainer() {
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowDarkColorScheme.primary,
            background = RepFlowDarkColorScheme.surfaceContainerHighest,
        )
    }

    @Test
    fun unselectedNavLabelsClearTheTextFloorInBothThemes() {
        assertUnselectedNavLabelClears(RepFlowDarkColorScheme, RepFlowColor.navUnselectedAlphaDark)
        assertUnselectedNavLabelClears(RepFlowLightColorScheme, RepFlowColor.navUnselectedAlphaLight)
    }

    @Test
    fun selectedNavPillLabelsClearTheTextFloorInBothThemes() {
        assertSelectedNavPillLabelClears(
            scheme = RepFlowDarkColorScheme,
            indicatorAlpha = RepFlowColor.navSelectedIndicatorAlphaDark,
            label = RepFlowColor.accent300,
        )
        assertSelectedNavPillLabelClears(
            scheme = RepFlowLightColorScheme,
            indicatorAlpha = RepFlowColor.navSelectedIndicatorAlphaLight,
            label = RepFlowLightColorScheme.primary,
        )
    }

    private fun assertUnselectedNavLabelClears(
        scheme: ColorScheme,
        alpha: Float,
    ) {
        val bar = scheme.surfaceContainer
        assertClears(
            floor = TEXT_FLOOR,
            foreground = scheme.onSurface.copy(alpha = alpha).compositeOver(bar),
            background = bar,
        )
    }

    private fun assertSelectedNavPillLabelClears(
        scheme: ColorScheme,
        indicatorAlpha: Float,
        label: Color,
    ) {
        val pill = scheme.primary.copy(alpha = indicatorAlpha).compositeOver(scheme.surfaceContainer)
        assertClears(floor = TEXT_FLOOR, foreground = label, background = pill)
    }

    private fun assertClears(
        floor: Double,
        foreground: Color,
        background: Color,
    ) {
        val measured = contrastRatio(foreground, background)
        assertTrue(
            "expected at least $floor:1, measured ${"%.2f".format(measured)}:1",
            measured >= floor,
        )
    }

    // endregion

    private companion object {
        /** WCAG AA for body text. */
        const val TEXT_FLOOR = 4.5

        fun contrastRatio(
            foreground: Color,
            background: Color,
        ): Double {
            val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
            val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun relativeLuminance(color: Color): Double =
            0.2126 * linearize(color.red) +
                0.7152 * linearize(color.green) +
                0.0722 * linearize(color.blue)

        fun linearize(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
    }
}
