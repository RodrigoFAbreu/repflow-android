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
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow
import kotlin.math.roundToInt

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

    /*
     * Remediation-1 CP3's structural primitives. Each one that renders text
     * names the ground it renders on; the one secondary-text colour serves
     * the section label, the list row's meta line, the scale row's end
     * labels, the stat tile's caption and the keypad's title, so it is
     * measured on every ground any of them sits on: the page (`background`),
     * a card, tile or sheet (`surface`), and the bottom bar
     * (`surfaceContainer`).
     */

    @Test
    fun secondaryTextClearsTheTextFloorOnEveryGroundItSitsOnInBothThemes() {
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            for (ground in listOf(scheme.background, scheme.surface, scheme.surfaceContainer)) {
                assertClears(
                    floor = TEXT_FLOOR,
                    foreground = repFlowSecondaryTextColor(scheme).compositeOver(ground),
                    background = ground,
                )
            }
        }
    }

    @Test
    fun theDesignsOwnTertiaryAlphaWouldMissTheFloorWhichIsWhyTheLabelsAreLifted() {
        // `6b`'s "45% tertiary and labels", measured on the ground a section
        // label most often sits on. If this ever clears, the lift in
        // RepFlowColor's text-tier note (deviation D39) can be revisited.
        val designTertiary =
            RepFlowDarkColorScheme.onSurface
                .copy(alpha = DESIGN_TERTIARY_ALPHA)
                .compositeOver(RepFlowDarkColorScheme.surface)
        assertTrue(contrastRatio(designTertiary, RepFlowDarkColorScheme.surface) < TEXT_FLOOR)
        assertEquals(0.55f, RepFlowColor.secondaryTextAlphaDark, 0f)
    }

    @Test
    fun statTileFiguresClearTheTextFloorOnTheTileGroundInBothThemes() {
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            assertClears(floor = TEXT_FLOOR, foreground = scheme.onSurface, background = scheme.surface)
        }
    }

    @Test
    fun keypadDigitsClearTheTextFloorOnTheKeyFillInBothThemes() {
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
    fun theSheetRendersOnSurfaceNotTheUnassignedContainerRole() {
        // The role decision: `surfaceContainerLow` (ModalBottomSheet's own
        // default) is never read, so every sheet's text sits on `surface`.
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            assertEquals(scheme.surface, repFlowSheetContainerColor(scheme))
            assertClears(floor = TEXT_FLOOR, foreground = scheme.onSurface, background = repFlowSheetContainerColor(scheme))
        }
        // Dark `surface` is the design's own sheet fill, `#232532`.
        assertEquals(Color(0xFF232532), repFlowSheetContainerColor(RepFlowDarkColorScheme))
    }

    @Test
    fun theBottomBarsSecondaryLabelClearsTheTextFloorOnTheBarFillInBothThemes() {
        // The bar's fill is a new ground for the neutral-outline label beside
        // the primary; the primary's own label is pinned above.
        for (scheme in listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme)) {
            val bar = scheme.surfaceContainer
            val label =
                scheme.onSurface
                    .copy(alpha = RepFlowButtonDefaults.neutralOutlineLabelAlpha)
                    .compositeOver(bar)
            assertClears(floor = TEXT_FLOOR, foreground = label, background = bar)
        }
        // Dark's bar is the design's own `#1b1d2b`.
        assertEquals(Color(0xFF1B1D2B), RepFlowDarkColorScheme.surfaceContainer)
    }

    @Test
    fun theUpNextChipLabelClearsTheTextFloorInBothThemes() {
        val dark = repFlowUpNextChipColors(RepFlowDarkColorScheme)
        assertEquals(RepFlowColor.accent300, dark.label)
        for (ground in listOf(RepFlowDarkColorScheme.surface, RepFlowDarkColorScheme.background)) {
            assertClears(floor = TEXT_FLOOR, foreground = dark.label, background = dark.fill.compositeOver(ground))
        }
        val light = repFlowUpNextChipColors(RepFlowLightColorScheme)
        assertEquals(repFlowSelectedPillColors(RepFlowLightColorScheme), light)
        for (ground in listOf(RepFlowLightColorScheme.surface, RepFlowLightColorScheme.background)) {
            assertClears(floor = TEXT_FLOOR, foreground = light.label, background = light.fill.compositeOver(ground))
        }
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
                // Remediation-1 CP3.
                RepFlowListRowDefaults.minHeight,
                RepFlowListRowDefaults.tallMinHeight,
                RepFlowKeypadDefaults.keyMinHeight,
                RepFlowBottomActionBarDefaults.secondaryMinHeight,
                RepFlowScreenScaffoldDefaults.backButtonSize,
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
        assertEquals(0.20f, UP_NEXT_FILL_ALPHA_DARK, 0f)
    }

    @Test
    fun theStepperCarriesTheSpecSheetsGapAndValueButton() {
        assertEquals(6.dp, RepFlowStepperDefaults.gap)
        assertEquals(CornerSize(10.dp), RepFlowStepperDefaults.valueShape.topStart)
    }

    @Test
    fun theScaleRowCarriesTheSpecSheetsCellGapAndDigitSize() {
        assertEquals(5.dp, RepFlowPillPickerDefaults.scale.cellGap)
        assertEquals(13.5.sp, RepFlowPillPickerDefaults.scale.labelFontSize)
        // The pill row keeps the spacing it shipped with.
        assertEquals(8.dp, RepFlowPillPickerDefaults.pill.cellGap)
    }

    @Test
    fun listRowsSpanTheDesignsRowHeightRange() {
        assertEquals(56.dp, RepFlowListRowDefaults.minHeight)
        assertEquals(68.dp, RepFlowListRowDefaults.tallMinHeight)
    }

    @Test
    fun theSheetUsesTheTopOfTheDesignsSheetRadiusRangeOnTopOnly() {
        with(RepFlowSheetDefaults.shape) {
            assertEquals(CornerSize(16.dp), topStart)
            assertEquals(CornerSize(16.dp), topEnd)
            assertEquals(CornerSize(0.dp), bottomStart)
            assertEquals(CornerSize(0.dp), bottomEnd)
        }
        assertEquals(32.dp, RepFlowSheetDefaults.handleWidth)
        assertEquals(4.dp, RepFlowSheetDefaults.handleHeight)
    }

    @Test
    fun theSheetScrimIsTheDesignsOwnValue() {
        // rgba(15,17,28,.6)
        with(RepFlowColor.sheetScrim) {
            assertEquals(15, (red * 255).roundToInt())
            assertEquals(17, (green * 255).roundToInt())
            assertEquals(28, (blue * 255).roundToInt())
            assertEquals(0.6f, alpha, 0.01f)
        }
    }

    @Test
    fun theKeypadCarriesTheDesignsKeyGeometry() {
        assertEquals(56.dp, RepFlowKeypadDefaults.keyMinHeight)
        assertEquals(CornerSize(10.dp), RepFlowKeypadDefaults.keyShape.topStart)
        assertEquals(8.dp, RepFlowKeypadDefaults.keyGap)
        assertEquals(30.sp, RepFlowKeypadDefaults.valueFontSize)
    }

    @Test
    fun theStatTileReusesTheShapeSchemesCardRadius() {
        assertEquals(RepFlowShapeScheme.medium, RepFlowStatTileDefaults.shape)
        assertEquals(12.dp, RepFlowStatTileDefaults.padding)
        assertEquals(19.sp, RepFlowStatTileDefaults.valueFontSize)
    }

    @Test
    fun theBottomBarAndScreenFrameCarryTheDesignsPaddings() {
        assertEquals(12.dp, RepFlowBottomActionBarDefaults.topPadding)
        assertEquals(16.dp, RepFlowBottomActionBarDefaults.horizontalPadding)
        assertEquals(16.dp, RepFlowBottomActionBarDefaults.bottomPadding)
        assertEquals(18.dp, RepFlowScreenScaffoldDefaults.topLevelTitleTopPadding)
        assertEquals(17.sp, RepFlowScreenScaffoldDefaults.subScreenTitleFontSize)
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

        /** `6b`'s "45% tertiary and labels". */
        const val DESIGN_TERTIARY_ALPHA = 0.45f

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
