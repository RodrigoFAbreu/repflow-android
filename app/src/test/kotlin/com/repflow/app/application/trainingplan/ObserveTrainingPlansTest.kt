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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveTrainingPlansTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan-entity")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
    private val observeTrainingPlans = ObserveTrainingPlans(planRepository)

    private fun seedExercise(id: String = "bench-press"): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            requireSuccessExercise(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccessName(ExerciseName.create("Exercise $id")),
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

    private fun repsRow(exerciseId: String) =
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
    fun `observes plans ordered by normalized name`() =
        runTest {
            val exerciseId = seedExercise()
            requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Zzz Day", listOf(repsRow(exerciseId.value)))))
            requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Aaa Day", listOf(repsRow(exerciseId.value)))))

            val overviews = observeTrainingPlans().first()

            assertEquals(listOf("Aaa Day", "Zzz Day"), overviews.map { it.plan.name.value })
        }

    private fun requireSuccess(result: DomainResult<*, TrainingPlanOperationError>) {
        if (result is DomainResult.Failure) {
            throw AssertionError("Expected success but was failure: ${result.error}")
        }
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
