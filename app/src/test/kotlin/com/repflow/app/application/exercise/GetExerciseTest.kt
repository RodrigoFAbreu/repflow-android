package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class GetExerciseTest {
    private val repository = InMemoryExerciseRepository()
    private val getExercise = GetExercise(repository)

    @Test
    fun `returns the exercise when it exists`() =
        runTest {
            val exercise =
                requireSuccessDomain(
                    Exercise.create(
                        id = ExerciseId("existing-1"),
                        name = requireSuccessDomain(ExerciseName.create("Bench Press")),
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        instructions = null,
                        defaultLoadIncrement = null,
                        defaultRestDuration = null,
                        origin = ExerciseOrigin.CUSTOM,
                        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
                    ),
                )
            repository.seed(exercise)

            val result = getExercise(exercise.id)

            assertEquals(exercise, requireSuccessOp(result))
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = getExercise(ExerciseId("missing"))

            assertEquals(ExerciseOperationError.NotFound, requireFailureOp(result))
        }

    private fun <T> requireSuccessDomain(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireSuccessOp(result: DomainResult<T, ExerciseOperationError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailureOp(result: DomainResult<T, ExerciseOperationError>): ExerciseOperationError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }
}
