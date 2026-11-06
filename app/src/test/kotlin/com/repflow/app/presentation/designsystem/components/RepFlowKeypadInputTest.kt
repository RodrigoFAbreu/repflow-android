package com.repflow.app.presentation.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins [RepFlowKeypadInput]: the prototype's own key handling
 * (`RepFlow.dc.html:4616-4630`), with six characters at most, one decimal point
 * and none at all for whole-number fields, and an unparseable entry confirming
 * as nothing; `50h`'s opening on the value, selected; and `10l`'s m:ss pad.
 */
class RepFlowKeypadInputTest {
    private val empty = RepFlowKeypadEntry(text = "")

    private fun pressAll(
        keys: List<String>,
        start: RepFlowKeypadEntry = empty,
        allowDecimal: Boolean = true,
        durationEntry: Boolean = false,
    ): RepFlowKeypadEntry =
        keys.fold(start) { entry, key ->
            RepFlowKeypadInput.press(entry, key, allowDecimal, durationEntry)
        }

    private fun type(
        keys: String,
        allowDecimal: Boolean = true,
    ): String = pressAll(keys.map(Char::toString), allowDecimal = allowDecimal).text

    private fun seedOf(
        value: String,
        durationEntry: Boolean = false,
    ) = RepFlowKeypadInput.seed(BigDecimal(value), durationEntry)

    /** Typed on an m:ss pad; each list item is one key, so `00` can be pressed. */
    private fun clockOf(
        vararg keys: String,
        start: RepFlowKeypadEntry = empty,
    ): String =
        RepFlowKeypadInput.display(
            pressAll(keys.toList(), start, allowDecimal = false, durationEntry = true).text,
            durationEntry = true,
        )

    @Test
    fun theKeysAreTheDesignsGridInOrder() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"), RepFlowKeypadInput.keys())
    }

    @Test
    fun anMssPadHasDoubleZeroInTheDecimalCell() {
        assertEquals(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "00", "0", "⌫"),
            RepFlowKeypadInput.keys(durationEntry = true),
        )
    }

    @Test
    fun digitsAppendUpToSixCharacters() {
        assertEquals("82.5", type("82.5"))
        assertEquals("123456", type("1234567"))
    }

    @Test
    fun aSecondDecimalPointIsIgnored() {
        assertEquals("82.55", type("82.5.5"))
    }

    @Test
    fun aWholeNumberFieldIgnoresTheDecimalPoint() {
        assertEquals("85", type("8.5", allowDecimal = false))
    }

    @Test
    fun aDecimalPointOnAnEmptyPadGivesZeroPoint() {
        assertEquals("0.", type("."))
        assertEquals("0.5", type(".5"))
    }

    @Test
    fun backspaceDropsTheLastCharacterAndIsHarmlessWhenEmpty() {
        val typed = RepFlowKeypadEntry(text = "82.5")
        assertEquals("82.", RepFlowKeypadInput.press(typed, RepFlowKeypadInput.BACKSPACE, allowDecimal = true).text)
        assertEquals("", RepFlowKeypadInput.press(empty, RepFlowKeypadInput.BACKSPACE, allowDecimal = true).text)
    }

    @Test
    fun theHeaderShowsZeroBeforeTheFirstKey() {
        assertEquals("0", RepFlowKeypadInput.display(""))
        assertEquals("7", RepFlowKeypadInput.display("7"))
    }

    @Test
    fun parsingReturnsAnExactDecimalOrNothing() {
        assertEquals(BigDecimal("82.5"), RepFlowKeypadInput.parse("82.5"))
        assertEquals(BigDecimal("0.5"), RepFlowKeypadInput.parse(".5"))
        assertEquals(0, BigDecimal.ZERO.compareTo(RepFlowKeypadInput.parse("0.")))
        assertNull(RepFlowKeypadInput.parse(""))
        assertNull(RepFlowKeypadInput.parse("."))
    }

    // region Opening on the current value (`50h`)

    @Test
    fun noInitialValueOpensEmptyAndNotSelected() {
        assertEquals(RepFlowKeypadEntry(text = "", selected = false), RepFlowKeypadInput.seed(null, durationEntry = false))
        assertEquals(RepFlowKeypadEntry(text = "", selected = false), RepFlowKeypadInput.seed(null, durationEntry = true))
    }

    @Test
    fun aDecimalPadOpensOnTheValueTheStepperShows() {
        assertEquals(RepFlowKeypadEntry(text = "82.5", selected = true), seedOf("82.50"))
        assertEquals(RepFlowKeypadEntry(text = "80", selected = true), seedOf("80.0"))
    }

    @Test
    fun aDigitReplacesASelectedValue() {
        assertEquals(RepFlowKeypadEntry(text = "4"), pressAll(listOf("4"), seedOf("82.5")))
        assertEquals(RepFlowKeypadEntry(text = "45"), pressAll(listOf("4", "5"), seedOf("82.5")))
    }

    @Test
    fun aDigitEqualToASelectedValueStillEndsTheSelection() {
        assertEquals(RepFlowKeypadEntry(text = "8"), pressAll(listOf("8"), seedOf("8"), allowDecimal = false))
    }

    @Test
    fun aDecimalPointOnASelectedValueGivesZeroPoint() {
        assertEquals(RepFlowKeypadEntry(text = "0."), pressAll(listOf("."), seedOf("82.5")))
        assertEquals(RepFlowKeypadEntry(text = "0.5"), pressAll(listOf(".", "5"), seedOf("82.5")))
    }

    @Test
    fun aDecimalPointOnASelectedWholeNumberIsIgnoredAndTheValueStaysSelected() {
        assertEquals(seedOf("8"), pressAll(listOf("."), seedOf("8"), allowDecimal = false))
    }

    @Test
    fun backspaceClearsASelectedValue() {
        assertEquals(empty, pressAll(listOf(RepFlowKeypadInput.BACKSPACE), seedOf("82.5")))
    }

    @Test
    fun backspaceOnTypedTextAfterASelectedValueDropsOneCharacter() {
        assertEquals(
            RepFlowKeypadEntry(text = "4"),
            pressAll(listOf("4", "5", RepFlowKeypadInput.BACKSPACE), seedOf("82.5")),
        )
    }

    @Test
    fun aSeedLongerThanTheLimitIsNotCutAndConfirmsBackUnchanged() {
        val seed = seedOf("1234.75")
        assertEquals("1234.75", seed.text)
        assertEquals(BigDecimal("1234.75"), RepFlowKeypadInput.parse(seed.text))
    }

    // endregion

    // region The m:ss pad (`10l`)

    @Test
    fun digitsFillFromTheRightLikeAClock() {
        assertEquals("0:00", clockOf())
        assertEquals("0:02", clockOf("2"))
        assertEquals("0:21", clockOf("2", "1"))
        assertEquals("2:15", clockOf("2", "1", "5"))
        assertEquals("1:30", clockOf("1", "3", "0"))
        assertEquals("0:45", clockOf("4", "5"))
        assertEquals("15:00", clockOf("1", "5", "0", "0"))
    }

    @Test
    fun leadingZerosAreIgnored() {
        assertEquals("0:00", clockOf("0"))
        assertEquals("0:00", clockOf("00"))
        assertEquals("0:05", clockOf("0", "5"))
        assertEquals("0:05", clockOf("00", "5"))
    }

    @Test
    fun doubleZeroAddsTwoZeros() {
        assertEquals("1:00", clockOf("1", "00"))
        assertEquals("15:00", clockOf("1", "5", "00"))
    }

    @Test
    fun aZeroKeyOnASelectedValueEmptiesIt() {
        val seed = seedOf("90", durationEntry = true)
        assertEquals("0:00", clockOf("0", start = seed))
        assertEquals("0:00", clockOf("00", start = seed))
        assertFalse(pressAll(listOf("0"), seed, allowDecimal = false, durationEntry = true).selected)
        assertFalse(pressAll(listOf("00"), seed, allowDecimal = false, durationEntry = true).selected)
    }

    @Test
    fun aDigitReplacesASelectedClock() {
        assertEquals("0:07", clockOf("7", start = seedOf("90", durationEntry = true)))
    }

    @Test
    fun backspaceClearsASelectedClock() {
        assertEquals(
            empty,
            pressAll(
                listOf(RepFlowKeypadInput.BACKSPACE),
                seedOf("90", durationEntry = true),
                allowDecimal = false,
                durationEntry = true,
            ),
        )
    }

    @Test
    fun aSixthDigitIsIgnoredAndDoubleZeroAddsOnlyWhatFits() {
        assertEquals("123:45", clockOf("1", "2", "3", "4", "5", "6"))
        assertEquals("123:40", clockOf("1", "2", "3", "4", "00"))
    }

    @Test
    fun aDecimalPointIsIgnoredOnAnMssPad() {
        assertEquals("0:01", clockOf("1", "."))
        val seed = seedOf("90", durationEntry = true)
        assertEquals(seed, pressAll(listOf("."), seed, allowDecimal = true, durationEntry = true))
    }

    @Test
    fun setConfirmsMinutesTimesSixtyPlusSeconds() {
        assertEquals(BigDecimal(90), RepFlowKeypadInput.parse("90", durationEntry = true))
        assertEquals(BigDecimal(90), RepFlowKeypadInput.parse("130", durationEntry = true))
        assertEquals(BigDecimal(5), RepFlowKeypadInput.parse("5", durationEntry = true))
        assertEquals(BigDecimal(900), RepFlowKeypadInput.parse("1500", durationEntry = true))
        assertNull(RepFlowKeypadInput.parse("", durationEntry = true))
    }

    @Test
    fun aSeedOfZeroIsEmptyAndSelectedAndConfirmsNothing() {
        val seed = seedOf("0", durationEntry = true)
        assertEquals(RepFlowKeypadEntry(text = "", selected = true), seed)
        assertEquals("0:00", RepFlowKeypadInput.display(seed.text, durationEntry = true))
        assertNull(RepFlowKeypadInput.parse(seed.text, durationEntry = true))
    }

    @Test
    fun everySeedConfirmsBackToTheSameSeconds() {
        listOf(5L, 45L, 60L, 90L, 900L, 7200L, 999999L).forEach { seconds ->
            val seed = RepFlowKeypadInput.seed(BigDecimal(seconds), durationEntry = true)
            assertTrue(seed.selected)
            assertEquals(BigDecimal(seconds), RepFlowKeypadInput.parse(seed.text, durationEntry = true))
        }
    }

    @Test
    fun aSeedReadsAsTheFormattersClock() {
        listOf(0L, 5L, 45L, 60L, 90L, 900L, 7200L, 999999L).forEach { seconds ->
            val seed = RepFlowKeypadInput.seed(BigDecimal(seconds), durationEntry = true)
            assertEquals(formatDuration(seconds), RepFlowKeypadInput.display(seed.text, durationEntry = true))
        }
    }

    // endregion
}
