package com.repflow.app.domain.exercise

import com.repflow.app.domain.common.DomainResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseInstructionsTest {
    @Test
    fun `null input becomes null instructions`() {
        val result = ExerciseInstructions.createOrNull(null)

        assertEquals(DomainResult.Success<ExerciseInstructions?>(null), result)
    }

    @Test
    fun `blank input becomes null instructions`() {
        val result = ExerciseInstructions.createOrNull("   \n  ")

        assertEquals(DomainResult.Success<ExerciseInstructions?>(null), result)
    }

    @Test
    fun `non-blank input is trimmed and kept`() {
        val result = ExerciseInstructions.createOrNull("  Keep your back straight.  ")

        val instructions = assertSuccess(result)
        assertEquals("Keep your back straight.", instructions?.value)
    }

    @Test
    fun `input over 2000 characters is rejected`() {
        val tooLong = "a".repeat(ExerciseInstructions.MAX_LENGTH + 1)

        val result = ExerciseInstructions.createOrNull(tooLong)

        assertEquals(DomainResult.Failure(ExerciseValidationError.InstructionsTooLong), result)
    }

    @Test
    fun `input of exactly 2000 characters is accepted`() {
        val exactlyMax = "a".repeat(ExerciseInstructions.MAX_LENGTH)

        val result = assertSuccess(ExerciseInstructions.createOrNull(exactlyMax))

        assertEquals(ExerciseInstructions.MAX_LENGTH, result?.value?.length)
    }

    private fun assertSuccess(result: DomainResult<ExerciseInstructions?, ExerciseValidationError>): ExerciseInstructions? =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
