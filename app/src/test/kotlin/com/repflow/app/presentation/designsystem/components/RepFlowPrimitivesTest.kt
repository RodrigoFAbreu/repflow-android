package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowDarkExtraColors
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import com.repflow.app.presentation.designsystem.RepFlowLightExtraColors
import com.repflow.app.presentation.designsystem.RepFlowShapeScheme
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.isDarkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Pins the primitives' own design values - the ones a composable body cannot
 * be asked about from a plain-JVM test, but which are just as much a
 * transcription of the design as `RepFlowColor.kt`'s hexes are.
 *
 * The colour assertions read every input (scheme, ramp step, alpha) from
 * production rather than restating it, and pin a *floor* rather than the
 * ratio measured today, so a later edit to a scheme or a shared alpha has to
 * keep clearing it. The WCAG arithmetic below deliberately mirrors
 * `RepFlowThemeTest`'s own copy: each test class computes its ratios
 * independently rather than sharing a helper whose one bug would move both.
 */
class RepFlowPrimitivesTest {
    // region Which theme a primitive thinks it is in

    @Test
    fun eachSchemeIsRecognisedAsItsOwnTheme() {
        assertTrue(isDarkColorScheme(RepFlowDarkColorScheme))
        assertFalse(isDarkColorScheme(RepFlowLightColorScheme))
    }

    @Test
    fun theAccentOutlinePairReadsADifferentRampStepPerTheme() {
        // Reusing dark's pair in light would put accent-300 on the light
        // surface: 1.38:1, which reads as a blank button.
        val dark = repFlowAccentOutlineColors(RepFlowDarkColorScheme)
        assertEquals(RepFlowColor.accent700, dark.border)
        assertEquals(RepFlowColor.accent300, dark.label)

        val light = repFlowAccentOutlineColors(RepFlowLightColorScheme)
        assertEquals(RepFlowColor.accent600, light.border)
        assertEquals(RepFlowColor.accent700, light.label)
    }

    // endregion

    // region Contrast floors the primitives own

    @Test
    fun accentOutlineLabelsClearTheTextFloorInBothThemes() {
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            assertClears(
                floor = TEXT_FLOOR,
                foreground = repFlowAccentOutlineColors(scheme).label,
                background = scheme.surface,
            )
        }
    }

    @Test
    fun neutralOutlineLabelsClearTheTextFloorInBothThemes() {
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            val label =
                scheme.onSurface
                    .copy(alpha = RepFlowButtonDefaults.neutralOutlineLabelAlpha)
                    .compositeOver(scheme.surface)
            assertClears(floor = TEXT_FLOOR, foreground = label, background = scheme.surface)
        }
    }

    @Test
    fun primaryButtonLabelsClearTheTextFloorInBothThemes() {
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            assertClears(floor = TEXT_FLOOR, foreground = scheme.onPrimary, background = scheme.primary)
        }
    }

    @Test
    fun theDarkSelectedPillLabelClearsTheTextFloorOnEverySurfaceItSitsOn() {
        // The fill is translucent, so the ground behind it is part of the
        // measurement: a pill sits either on a card or on the page itself.
        val selected = repFlowSelectedPillColors(RepFlowDarkColorScheme)
        for (ground in listOf(RepFlowDarkColorScheme.surface, RepFlowDarkColorScheme.background)) {
            assertClears(
                floor = TEXT_FLOOR,
                foreground = selected.label,
                background = selected.fill.compositeOver(ground),
            )
        }
    }

    @Test
    fun theLightSelectedPillReusesTheConfirmedAccentOutlinePair() {
        // Light has no scale-row or status-chip render in the design to read a
        // value from; this is the disclosed fallback, not a design fact.
        val selected = repFlowSelectedPillColors(RepFlowLightColorScheme)
        val accent = repFlowAccentOutlineColors(RepFlowLightColorScheme)
        assertEquals(Color.Transparent, selected.fill)
        assertEquals(accent.border, selected.border)
        assertEquals(accent.label, selected.label)
        assertClears(
            floor = TEXT_FLOOR,
            foreground = selected.label,
            background = RepFlowLightColorScheme.surface,
        )
    }

    @Test
    fun pendingChipLabelsClearTheTextFloorOnTheControlFillInBothThemes() {
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowDarkColorScheme.onSurface,
            background = RepFlowDarkExtraColors.control,
        )
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowLightColorScheme.onSurface,
            background = RepFlowLightExtraColors.control,
        )
    }

    @Test
    fun theAccentCardCarriesAReadableContentColourInBothThemes() {
        // Its fill is a theme-independent constant, so one measurement covers
        // both themes - which is exactly why it cannot use `onSurface`.
        assertClears(
            floor = TEXT_FLOOR,
            foreground = RepFlowCardDefaults.accentContentColor,
            background = RepFlowColor.accent900,
        )
        assertEquals(RepFlowDarkColorScheme.onSurface, RepFlowCardDefaults.accentContentColor)
    }

    // endregion

    // region Dimensions and radii

    @Test
    fun everyInteractivePrimitiveMeetsTheMinimumTapTarget() {
        val heights =
            listOf<Dp>(
                RepFlowButtonDefaults.primaryMinHeight,
                RepFlowButtonDefaults.accentOutlineMinHeight,
                RepFlowButtonDefaults.neutralOutlineMinHeight,
                RepFlowPillPickerDefaults.pill.minHeight,
                RepFlowPillPickerDefaults.scale.minHeight,
                RepFlowStepperDefaults.compact.buttonSize,
                RepFlowStepperDefaults.large.buttonSize,
            )
        for (height in heights) {
            assertTrue("expected at least $MIN_TAP_TARGET, was $height", height >= MIN_TAP_TARGET)
        }
    }

    @Test
    fun theButtonTiersCarryTheirConfirmedHeightsAndLabelSizes() {
        assertEquals(56.dp, RepFlowButtonDefaults.primaryMinHeight)
        assertEquals(16.sp, RepFlowButtonDefaults.primaryFontSize)
        assertEquals(48.dp, RepFlowButtonDefaults.accentOutlineMinHeight)
        assertEquals(14.5.sp, RepFlowButtonDefaults.accentOutlineFontSize)
        assertEquals(44.dp, RepFlowButtonDefaults.neutralOutlineMinHeight)
        assertEquals(14.sp, RepFlowButtonDefaults.neutralOutlineFontSize)
        assertEquals(0.8f, RepFlowButtonDefaults.neutralOutlineLabelAlpha, 0f)
    }

    @Test
    fun onlyThePrimaryButtonLabelUsesTheDesignsSingleWeight600Slot() {
        assertEquals(FontWeight.SemiBold, RepFlowButtonDefaults.primaryFontWeight)
    }

    @Test
    fun theButtonAndCardRadiiReuseTheShapeSchemeWhereverItAlreadyHasThem() {
        assertEquals(RepFlowShapeScheme.medium, RepFlowButtonDefaults.primaryShape)
        assertEquals(RepFlowShapeScheme.small, RepFlowButtonDefaults.neutralOutlineShape)
        assertEquals(RepFlowShapeScheme.large, RepFlowCardDefaults.shape)
        // The one radius the scheme has no step for: the accent tier's 10dp
        // sits inside the design's 10-12 button range, which `medium` rounds up.
        assertEquals(CornerSize(10.dp), RepFlowButtonDefaults.accentOutlineShape.topStart)
    }

    @Test
    fun theStepperCarriesBothOfTheDesignsSizes() {
        with(RepFlowStepperDefaults.compact) {
            assertEquals(44.dp, buttonSize)
            assertEquals(RepFlowShapes.stepper, buttonShape)
            assertEquals(24.sp, valueFontSize)
        }
        with(RepFlowStepperDefaults.large) {
            assertEquals(48.dp, buttonSize)
            assertEquals(32.sp, valueFontSize)
        }
    }

    @Test
    fun thePillPickerCarriesBothOfTheDesignsCellShapes() {
        assertEquals(RepFlowShapes.pill, RepFlowPillPickerDefaults.pill.shape)
        val scaleCell = RepFlowPillPickerDefaults.scale.shape as RoundedCornerShape
        assertEquals(CornerSize(8.dp), scaleCell.topStart)
    }

    @Test
    fun theDarkSelectedFillKeepsTheDesignsOwnOpacity() {
        assertEquals(0.22f, SELECTED_FILL_ALPHA_DARK, 0f)
    }

    // endregion

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

    private companion object {
        /** WCAG AA for body text. */
        const val TEXT_FLOOR = 4.5

        /** The design's own layout rule: "every tap target is at least 44". */
        val MIN_TAP_TARGET = 44.dp

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
