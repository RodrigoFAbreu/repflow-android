package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CreateExerciseTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator()
    private val repository = InMemoryExerciseRepository()
    private val createExercise = CreateExercise(repository, clock, ids)

    private fun command(
        name: String = "Bench Press",
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        instructions: String? = null,
        loadIncrementGrams: Long? = null,
        restSeconds: Long? = null,
    ) = CreateExerciseCommand(name, trackingType, instructions, loadIncrementGrams, restSeconds)

    @Test
    fun `creates a new custom exercise with the generated id`() =
        runTest {
            val result = createExercise(command())

            val id = requireSuccess(result)
            val stored = requireNotNullExercise(repository.findById(id))
            assertEquals("Bench Press", stored.name.value)
            assertEquals(ExerciseOrigin.CUSTOM, stored.origin)
            assertEquals(now, stored.createdAt)
            assertEquals(now, stored.updatedAt)
        }

    @Test
    fun `rejects a duplicate name detected by the precheck`() =
        runTest {
            requireSuccess(createExercise(command(name = "Bench Press")))

            val result = createExercise(command(name = "  bench   press  "))

            assertEquals(ExerciseOperationError.DuplicateName, requireFailure(result))
        }

    @Test
    fun `rejects a duplicate name reported only by the repository`() =
        runTest {
            requireSuccess(createExercise(command(name = "Bench Press")))
            repository.nextInsertFailure = ExercisePersistenceError.DuplicateName

            val result = createExercise(command(name = "Squat"))

            assertEquals(ExerciseOperationError.DuplicateName, requireFailure(result))
        }

    @Test
    fun `rejects a blank name as a validation failure`() =
        runTest {
            val result = createExercise(command(name = "   "))

            val error = requireFailure(result)
            assertTrue(error is ExerciseOperationError.ValidationFailed)
            assertEquals(
                listOf(ExerciseValidationError.NameBlank),
                (error as ExerciseOperationError.ValidationFailed).errors,
            )
        }

    @Test
    fun `rejects a load increment on a tracking type that does not support load`() =
        runTest {
            val result =
                createExercise(
                    command(trackingType = ExerciseTrackingType.REPS_ONLY, loadIncrementGrams = 2_500),
                )

            val error = requireFailure(result)
            assertTrue(error is ExerciseOperationError.ValidationFailed)
            assertEquals(
                listOf(ExerciseValidationError.LoadIncrementNotSupported),
                (error as ExerciseOperationError.ValidationFailed).errors,
            )
        }

    @Test
    fun `surfaces an unavailable persistence failure`() =
        runTest {
            repository.nextInsertFailure = ExercisePersistenceError.Unavailable

            val result = createExercise(command())

            assertEquals(ExerciseOperationError.PersistenceUnavailable, requireFailure(result))
        }

    private fun <T> requireSuccess(result: DomainResult<T, ExerciseOperationError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireFailure(result: DomainResult<T, ExerciseOperationError>): ExerciseOperationError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }

    private fun requireNotNullExercise(exercise: Exercise?): Exercise =
        exercise ?: throw AssertionError("Expected the exercise to have been stored")
}
