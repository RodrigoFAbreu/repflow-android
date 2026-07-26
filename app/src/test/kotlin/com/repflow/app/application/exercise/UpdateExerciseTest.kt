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

class UpdateExerciseTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val updatedAt = Instant.parse("2026-01-02T00:00:00Z")
    private val clock = FixedClock(updatedAt)
    private val repository = InMemoryExerciseRepository()
    private val updateExercise = UpdateExercise(repository, clock)

    private fun seedExercise(
        id: String = "existing-1",
        name: String = "Bench Press",
        origin: ExerciseOrigin = ExerciseOrigin.CUSTOM,
    ): Exercise {
        val exercise =
            requireSuccessDomain(
                Exercise.create(
                    id = ExerciseId(id),
                    name = requireSuccessDomain(ExerciseName.create(name)),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = origin,
                    createdAt = createdAt,
                ),
            )
        repository.seed(exercise)
        return exercise
    }

    // Only `id`, `name` and `trackingType` are ever varied by a test below; the remaining
    // command fields are fixed to keep this helper's parameter count within the project's
    // LongParameterList budget instead of speculatively exposing fields nothing exercises yet.
    private fun command(
        id: ExerciseId,
        name: String = "Bench Press",
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    ) = UpdateExerciseCommand(
        id,
        name,
        trackingType,
        instructions = null,
        defaultLoadIncrementGrams = null,
        defaultRestSeconds = null,
    )

    @Test
    fun `updates fields while preserving createdAt and origin`() =
        runTest {
            val existing = seedExercise(origin = ExerciseOrigin.BUILT_IN)

            val result = updateExercise(command(id = existing.id, name = "Incline Bench Press"))

            requireSuccessOp(result)
            val stored = requireNotNullExercise(repository.findById(existing.id))
            assertEquals("Incline Bench Press", stored.name.value)
            assertEquals(createdAt, stored.createdAt)
            assertEquals(updatedAt, stored.updatedAt)
            assertEquals(ExerciseOrigin.BUILT_IN, stored.origin)
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = updateExercise(command(id = ExerciseId("missing")))

            assertEquals(ExerciseOperationError.NotFound, requireFailureOp(result))
        }

    @Test
    fun `duplicate-name check excludes the exercise being edited`() =
        runTest {
            val existing = seedExercise(name = "Bench Press")

            // Renaming to a whitespace/casing variant of its own current name must succeed.
            val result = updateExercise(command(id = existing.id, name = "  BENCH   press "))

            requireSuccessOp(result)
        }

    @Test
    fun `rejects renaming to another exercise's name`() =
        runTest {
            val first = seedExercise(id = "first", name = "Bench Press")
            seedExercise(id = "second", name = "Squat")

            val result = updateExercise(command(id = first.id, name = "Squat"))

            assertEquals(ExerciseOperationError.DuplicateName, requireFailureOp(result))
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

    private fun requireNotNullExercise(exercise: Exercise?): Exercise =
        exercise ?: throw AssertionError("Expected the exercise to have been stored")
}
