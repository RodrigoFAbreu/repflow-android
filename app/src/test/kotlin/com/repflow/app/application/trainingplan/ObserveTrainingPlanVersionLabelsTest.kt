package com.repflow.app.application.trainingplan

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveTrainingPlanVersionLabelsTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
    private val reviseTrainingPlan = ReviseTrainingPlan(planRepository, exerciseRepository, clock, ids)
    private val observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(planRepository)

    private suspend fun seedExercise(): ExerciseId {
        val exerciseId = ExerciseId("bench-press")
        val exercise =
            requireSuccessDomain(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccessDomain(ExerciseName.create("Bench Press")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            )
        exerciseRepository.seed(exercise)
        return exerciseId
    }

    private fun plannedExerciseInput(exerciseId: String) =
        PlannedExerciseInput(
            exerciseId = exerciseId,
            order = 0,
            targetSets = 3,
            targetKind = PlannedExerciseTargetKind.REPS,
            repMin = 8,
            repMax = 12,
            durationMinSeconds = null,
            durationMaxSeconds = null,
            restSeconds = 90,
            isOptional = false,
        )

    @Test
    fun `maps every historical version of a revised plan back to its identity and current name`() =
        runTest {
            val exerciseId = seedExercise()
            val planId =
                requireSuccessOp(
                    createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(plannedExerciseInput(exerciseId.value)))),
                )
            val firstVersion = requireNotNull(planRepository.findOverviewByPlanId(planId)).latestVersion.id
            requireSuccessOp(
                reviseTrainingPlan(
                    ReviseTrainingPlanCommand(planId.value, "Push Day V2", listOf(plannedExerciseInput(exerciseId.value))),
                ),
            )
            val secondVersion = requireNotNull(planRepository.findOverviewByPlanId(planId)).latestVersion.id

            val labels = observeTrainingPlanVersionLabels()

            assertEquals(planId, labels[firstVersion]?.planId)
            assertEquals("Push Day V2", labels[firstVersion]?.planName)
            assertEquals(planId, labels[secondVersion]?.planId)
            assertEquals("Push Day V2", labels[secondVersion]?.planName)
        }

    @Test
    fun `returns an empty map when there are no plans`() =
        runTest {
            assertEquals(emptyMap<TrainingPlanVersionId, TrainingPlanVersionLabel>(), observeTrainingPlanVersionLabels())
        }

    private fun <T> requireSuccessDomain(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun <T> requireSuccessOp(result: DomainResult<T, TrainingPlanOperationError>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
