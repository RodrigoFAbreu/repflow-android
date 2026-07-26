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
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReviseTrainingPlanTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan-entity")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
    private val reviseTrainingPlan = ReviseTrainingPlan(planRepository, exerciseRepository, clock, ids)

    private fun seedExercise(id: String = "bench-press"): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            requireSuccessExercise(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccessName(ExerciseName.create("Bench Press $id")),
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

    private fun repsRow(
        exerciseId: String,
        order: Int = 0,
    ) = PlannedExerciseInput(
        exerciseId = exerciseId,
        order = order,
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
    fun `revising a plan creates a new version and preserves the previous one unchanged`() =
        runTest {
            val firstExercise = seedExercise("first")
            val secondExercise = seedExercise("second")
            val planId =
                requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(firstExercise.value)))))
            val laterInstant = now.plusSeconds(3_600)
            clock.advanceTo(laterInstant)

            requireSuccessUnit(
                reviseTrainingPlan(
                    ReviseTrainingPlanCommand(planId.value, "Push Day", listOf(repsRow(secondExercise.value))),
                ),
            )

            val versions = planRepository.versionsOf(planId)
            assertEquals(2, versions.size)
            assertEquals(1, versions[0].versionNumber)
            assertEquals(firstExercise, versions[0].plannedExercises.single().exerciseId)
            assertEquals(2, versions[1].versionNumber)
            assertEquals(secondExercise, versions[1].plannedExercises.single().exerciseId)
            val overview = requireNotNull(planRepository.findOverviewByPlanId(planId))
            assertEquals(2, overview.latestVersion.versionNumber)
            assertEquals(laterInstant, overview.plan.updatedAt)
        }

    @Test
    fun `revising a missing plan is reported as not found`() =
        runTest {
            val exerciseId = seedExercise()

            val result =
                reviseTrainingPlan(
                    ReviseTrainingPlanCommand(TrainingPlanId("missing-plan").value, "Push Day", listOf(repsRow(exerciseId.value))),
                )

            assertEquals(TrainingPlanOperationError.NotFound, requireFailure(result))
        }

    @Test
    fun `revising a plan's name to conflict with a different plan is rejected`() =
        runTest {
            val exerciseId = seedExercise()
            requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Pull Day", listOf(repsRow(exerciseId.value)))))
            val pushDayId =
                requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value)))))

            val result =
                reviseTrainingPlan(ReviseTrainingPlanCommand(pushDayId.value, "Pull Day", listOf(repsRow(exerciseId.value))))

            assertEquals(TrainingPlanOperationError.DuplicateName, requireFailure(result))
        }

    @Test
    fun `revising a plan and keeping its own name is not treated as a conflict`() =
        runTest {
            val exerciseId = seedExercise()
            val planId =
                requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push Day", listOf(repsRow(exerciseId.value)))))

            val result = reviseTrainingPlan(ReviseTrainingPlanCommand(planId.value, "Push Day", listOf(repsRow(exerciseId.value))))

            requireSuccessUnit(result)
        }

    private fun requireSuccess(result: DomainResult<TrainingPlanId, TrainingPlanOperationError>): TrainingPlanId =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun requireSuccessUnit(result: DomainResult<Unit, TrainingPlanOperationError>) {
        if (result is DomainResult.Failure) {
            throw AssertionError("Expected success but was failure: ${result.error}")
        }
    }

    private fun <T> requireFailure(result: DomainResult<T, TrainingPlanOperationError>): TrainingPlanOperationError =
        when (result) {
            is DomainResult.Success -> throw AssertionError("Expected failure but was success: ${result.value}")
            is DomainResult.Failure -> result.error
        }

    private fun requireSuccessExercise(result: DomainResult<Exercise, *>): Exercise =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private fun requireSuccessName(result: DomainResult<ExerciseName, *>): ExerciseName =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
