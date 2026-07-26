package com.repflow.app.application.exercise

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ArchiveExerciseTest {
    private val createdAt = Instant.parse("2026-01-01T00:00:00Z")
    private val archivedAt = Instant.parse("2026-01-02T00:00:00Z")
    private val clock = FixedClock(archivedAt)
    private val repository = InMemoryExerciseRepository()
    private val archiveExercise = ArchiveExercise(repository, clock)

    private fun seedExercise(): Exercise {
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
                    createdAt = createdAt,
                ),
            )
        repository.seed(exercise)
        return exercise
    }

    @Test
    fun `archives an active exercise and bumps updatedAt`() =
        runTest {
            val existing = seedExercise()

            val result = archiveExercise(existing.id)

            requireSuccessOp(result)
            val stored = requireNotNullExercise(repository.findById(existing.id))
            assertTrue(stored.isArchived)
            assertEquals(archivedAt, stored.archivedAt)
            assertEquals(archivedAt, stored.updatedAt)
            assertEquals(createdAt, stored.createdAt)
        }

    @Test
    fun `fails with AlreadyArchived for an already-archived exercise`() =
        runTest {
            val existing = seedExercise()
            requireSuccessOp(archiveExercise(existing.id))

            val result = archiveExercise(existing.id)

            assertEquals(ExerciseOperationError.AlreadyArchived, requireFailureOp(result))
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = archiveExercise(ExerciseId("missing"))

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
