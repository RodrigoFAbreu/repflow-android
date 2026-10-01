package com.repflow.app.presentation.home

import androidx.compose.ui.graphics.Color
import com.repflow.app.domain.recovery.ReadinessBand
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowLightColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The readiness band's two signals: a distinct word per band, and a colour
 * that is readable as text (the band word and a flagged factor's label are
 * drawn in it) on every ground the sheet and Home's card use, in both themes.
 * The WCAG arithmetic is this class's own copy, as `RepFlowPrimitivesTest`'s
 * note explains.
 */
class ReadinessBandStyleTest {
    @Test
    fun everyBandHasItsOwnWord() {
        val labels = ReadinessBand.entries.map(::readinessBandLabel)
        assertEquals(ReadinessBand.entries.size, labels.toSet().size)
    }

    @Test
    fun theDarkBandsAreThePrototypesOwnValues() {
        assertEquals(Color(0xFFB5ABFC), readinessBandColor(ReadinessBand.READY, RepFlowDarkColorScheme))
        assertEquals(Color(0xFFDBB970), readinessBandColor(ReadinessBand.HOLD, RepFlowDarkColorScheme))
        assertEquals(Color(0xFFEC9C63), readinessBandColor(ReadinessBand.BACK_OFF, RepFlowDarkColorScheme))
        assertEquals(Color(0xFFEB827B), readinessBandColor(ReadinessBand.PROTECT, RepFlowDarkColorScheme))
    }

    @Test
    fun everyBandColourIsDistinctWithinATheme() {
        listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme).forEach { scheme ->
            val colours = ReadinessBand.entries.map { readinessBandColor(it, scheme) }
            assertEquals(ReadinessBand.entries.size, colours.toSet().size)
        }
    }

    @Test
    fun everyBandColourClearsTheTextFloorOnSurfaceAndBackgroundInBothThemes() {
        listOf(RepFlowDarkColorScheme, RepFlowLightColorScheme).forEach { scheme ->
            ReadinessBand.entries.forEach { band ->
                val colour = readinessBandColor(band, scheme)
                listOf(scheme.surface, scheme.background).forEach { ground ->
                    val measured = contrastRatio(colour, ground)
                    assertTrue(
                        "$band on $ground: expected at least $TEXT_FLOOR:1, measured ${"%.2f".format(measured)}:1",
                        measured >= TEXT_FLOOR,
                    )
                }
            }
        }
    }

    private companion object {
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
