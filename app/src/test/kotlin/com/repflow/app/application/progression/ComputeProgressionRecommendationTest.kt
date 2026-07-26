package com.repflow.app.application.progression

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.InMemoryFutsalRepository
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.domain.recovery.RecoveryEntryId
import com.repflow.app.domain.trainingplan.RepRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ComputeProgressionRecommendationTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val repository = InMemoryProgressionRecommendationRepository()
    private val recoveryRepository = InMemoryRecoveryRepository()
    private val futsalRepository = InMemoryFutsalRepository()
    private val useCase =
        ComputeProgressionRecommendation(
            repository = repository,
            getWorkoutDayContext = GetWorkoutDayContext(recoveryRepository, futsalRepository, clock),
            clock = clock,
            idGenerator = SequentialIdentifierGenerator(prefix = "rec"),
        )

    private fun repRange(
        min: Int,
        max: Int,
    ) = (RepRange.create(min, max) as DomainResult.Success).value

    @Test
    fun `computes and persists a recommendation reflecting only performance when recovery is unremarkable`() =
        runTest {
            val result =
                useCase(
                    ComputeProgressionRecommendationCommand(
                        exerciseId = ExerciseId("exercise-1"),
                        workingSetReps = listOf(12, 12, 13),
                        workingSetRpe = listOf(7.0, 7.0, 7.0),
                        plannedRepRange = repRange(8, 12),
                    ),
                )
            val recommendation = (result as DomainResult.Success).value
            assertEquals(ProgressionResult.IncreaseLoad, recommendation.result)
            assertEquals(recommendation, repository.findLatestForExercise(ExerciseId("exercise-1")))
        }

    @Test
    fun `recovery context overrides performance-based results`() =
        runTest {
            recoveryRepository.upsert(
                (
                    RecoveryEntry.create(
                        id = RecoveryEntryId("entry-1"),
                        date = LocalDate.of(2026, 1, 1),
                        sleepQuality = 2,
                        energy = 2,
                        legDoms = 2,
                        heelStiffness = 2,
                        painWhileWalking = 3,
                        heavyLegs = 0,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                        createdAt = now,
                        updatedAt = now,
                    ) as DomainResult.Success
                ).value,
            )
            val result =
                useCase(
                    ComputeProgressionRecommendationCommand(
                        exerciseId = ExerciseId("exercise-1"),
                        workingSetReps = listOf(12, 12, 13),
                        workingSetRpe = listOf(7.0, 7.0, 7.0),
                        plannedRepRange = repRange(8, 12),
                    ),
                )
            val recommendation = (result as DomainResult.Success).value
            assertEquals(ProgressionResult.RecoveryAdjustment, recommendation.result)
        }
}
