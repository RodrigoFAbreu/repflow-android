package com.repflow.app.presentation.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Pins [RepFlowKeypadInput] to the prototype's own key handling
 * (`RepFlow.dc.html:4616-4630`): six characters at most, one decimal point and
 * none at all for whole-number fields, backspace drops one character, and an
 * unparseable entry confirms as nothing.
 */
class RepFlowKeypadInputTest {
    private fun type(
        keys: String,
        allowDecimal: Boolean = true,
    ): String = keys.fold("") { text, key -> RepFlowKeypadInput.press(text, key.toString(), allowDecimal) }

    @Test
    fun theKeysAreTheDesignsGridInOrder() {
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫"), RepFlowKeypadInput.keys)
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
    fun backspaceDropsTheLastCharacterAndIsHarmlessWhenEmpty() {
        assertEquals("82.", RepFlowKeypadInput.press("82.5", RepFlowKeypadInput.BACKSPACE, allowDecimal = true))
        assertEquals("", RepFlowKeypadInput.press("", RepFlowKeypadInput.BACKSPACE, allowDecimal = true))
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
        assertNull(RepFlowKeypadInput.parse(""))
        assertNull(RepFlowKeypadInput.parse("."))
    }
}
