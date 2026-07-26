package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full normalization matrix approved as D-25/D-26: NFKC first, then
 * trim, then whitespace collapse, then (for the key only) lower-casing with
 * [java.util.Locale.ROOT].
 */
class ExerciseNameTest {
    @Test
    fun `plain name is preserved as-is for display and lowercased for the key`() {
        val result = ExerciseName.create("Bench Press")

        val name = assertSuccess(result)
        assertEquals("Bench Press", name.value)
        assertEquals("bench press", name.key)
    }

    @Test
    fun `differing casing normalizes to the same key`() {
        val lower = assertSuccess(ExerciseName.create("bench press"))
        val upper = assertSuccess(ExerciseName.create("BENCH PRESS"))
        val mixed = assertSuccess(ExerciseName.create("BeNcH pReSs"))

        assertEquals(lower.key, upper.key)
        assertEquals(lower.key, mixed.key)
    }

    @Test
    fun `leading and trailing whitespace is trimmed from the display value`() {
        val name = assertSuccess(ExerciseName.create("   Bench Press   "))

        assertEquals("Bench Press", name.value)
        assertEquals("bench press", name.key)
    }

    @Test
    fun `repeated internal whitespace collapses to a single space`() {
        val name = assertSuccess(ExerciseName.create("Bench    Press"))

        assertEquals("Bench Press", name.value)
        assertEquals("bench press", name.key)
    }

    @Test
    fun `non-breaking space is treated as whitespace and collapsed`() {
        // U+00A0 NO-BREAK SPACE
        val name = assertSuccess(ExerciseName.create("Bench\u00A0Press"))

        assertEquals("Bench Press", name.value)
        assertEquals("bench press", name.key)
    }

    @Test
    fun `NFKC folds full-width and ideographic spaces before trimming`() {
        // U+3000 IDEOGRAPHIC SPACE normalizes (NFKC) to U+0020 before trim,
        // so a name made only of ideographic spaces must be rejected as
        // blank rather than accepted as a name of literal full-width spaces.
        val result = ExerciseName.create("\u3000\u3000")

        assertEquals(DomainResult.Failure(ExerciseValidationError.NameBlank), result)
    }

    @Test
    fun `accents are preserved in the display value and the key`() {
        val name = assertSuccess(ExerciseName.create("Elevação"))

        assertEquals("Elevação", name.value)
        assertEquals("elevação", name.key)
    }

    @Test
    fun `accented and unaccented names produce different keys`() {
        val accented = assertSuccess(ExerciseName.create("Elevação"))
        val plain = assertSuccess(ExerciseName.create("Elevacao"))

        assertTrue(accented.key != plain.key)
    }

    @Test
    fun `blank name is rejected`() {
        val result = ExerciseName.create("   ")

        assertEquals(DomainResult.Failure(ExerciseValidationError.NameBlank), result)
    }

    @Test
    fun `empty name is rejected`() {
        val result = ExerciseName.create("")

        assertEquals(DomainResult.Failure(ExerciseValidationError.NameBlank), result)
    }

    @Test
    fun `name longer than 80 characters after cleaning is rejected`() {
        val tooLong = "A".repeat(ExerciseName.MAX_LENGTH + 1)

        val result = ExerciseName.create(tooLong)

        assertEquals(DomainResult.Failure(ExerciseValidationError.NameTooLong), result)
    }

    @Test
    fun `name of exactly 80 characters after cleaning is accepted`() {
        val exactlyMax = "A".repeat(ExerciseName.MAX_LENGTH)

        val name = assertSuccess(ExerciseName.create(exactlyMax))

        assertEquals(ExerciseName.MAX_LENGTH, name.value.length)
    }

    @Test
    fun `surrounding whitespace does not count toward the length limit`() {
        val padded = "  " + "A".repeat(ExerciseName.MAX_LENGTH) + "  "

        val name = assertSuccess(ExerciseName.create(padded))

        assertEquals(ExerciseName.MAX_LENGTH, name.value.length)
    }

    private fun assertSuccess(result: DomainResult<ExerciseName, ExerciseValidationError>): ExerciseName =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
