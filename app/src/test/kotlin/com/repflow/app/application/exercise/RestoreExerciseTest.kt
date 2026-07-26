package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

class RestoreExerciseTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val archivedAt = Instant.parse("2026-01-02T00:00:00Z")
    private val restoredAt = Instant.parse("2026-01-03T00:00:00Z")
    private val clock = FixedClock(restoredAt)
    private val repository = InMemoryExerciseRepository()
    private val restoreExercise = RestoreExercise(repository, clock)

    private fun seedArchivedExercise(): Exercise {
        val active =
            requireSuccessDomain(
                Exercise.create(
                    id = ExerciseId("existing-1"),
                    name = requireSuccessDomain(ExerciseName.create("Bench Press")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = createdAt,
                ),
            )
        val archived = active.archive(archivedAt)
        repository.seed(archived)
        return archived
    }

    @Test
    fun `restores an archived exercise and bumps updatedAt`() =
        runTest {
            val existing = seedArchivedExercise()

            val result = restoreExercise(existing.id)

            requireSuccessOp(result)
            val stored = requireNotNullExercise(repository.findById(existing.id))
            assertFalse(stored.isArchived)
            assertEquals(restoredAt, stored.updatedAt)
            assertEquals(createdAt, stored.createdAt)
        }

    @Test
    fun `restoring an already-active exercise is an idempotent no-op`() =
        runTest {
            val existing = seedArchivedExercise()
            requireSuccessOp(restoreExercise(existing.id))

            val result = restoreExercise(existing.id)

            requireSuccessOp(result)
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = restoreExercise(ExerciseId("missing"))

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

    private fun requireNotNullExercise(exercise: Exercise?): Exercise =
        exercise ?: throw AssertionError("Expected the exercise to have been stored")
}
