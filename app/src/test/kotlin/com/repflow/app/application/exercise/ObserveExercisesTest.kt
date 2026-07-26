package com.repflow.app.application.exercise

import app.cash.turbine.test
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

class ObserveExercisesTest {
    private val repository = InMemoryExerciseRepository()
    private val observeExercises = ObserveExercises(repository)

    private fun exercise(
        id: String,
        name: String,
    ): Exercise =
        requireSuccessDomain(
            Exercise.create(
                id = ExerciseId(id),
                name = requireSuccessDomain(ExerciseName.create(name)),
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
        )

    @Test
    fun `normalizes the raw query the same way a persisted name would be`() =
        runTest {
            repository.seed(exercise("1", "Elevação Lateral"))
            repository.seed(exercise("2", "Bench Press"))

            observeExercises(ExerciseStatusFilter.ACTIVE, "  ELEVAÇÃO   lateral ").test {
                val items = awaitItem()
                assertEquals(listOf("Elevação Lateral"), items.map { it.name.value })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an empty or blank raw query applies no filter`() =
        runTest {
            repository.seed(exercise("2", "Bench Press"))
            repository.seed(exercise("1", "Squat"))

            observeExercises(ExerciseStatusFilter.ACTIVE, "   ").test {
                val items = awaitItem()
                // Ordered by name_key ascending (D-27): "bench press" < "squat".
                assertEquals(listOf("Bench Press", "Squat"), items.map { it.name.value })
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun <T> requireSuccessDomain(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
