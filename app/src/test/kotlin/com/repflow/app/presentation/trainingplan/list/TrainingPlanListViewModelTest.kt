package com.repflow.app.presentation.trainingplan.list

import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPlanListViewModelTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
    private val viewModel = TrainingPlanListViewModel(ObserveTrainingPlans(planRepository))

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun seedExercise(id: String = "bench-press"): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccess(ExerciseName.create("Exercise $id")),
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

    private suspend fun seedPlan(
        name: String,
        exerciseId: ExerciseId,
    ) {
        val command =
            CreateTrainingPlanCommand(
                name = name,
                plannedExercises =
                    listOf(
                        PlannedExerciseInput(
                            exerciseId = exerciseId.value,
                            order = 0,
                            targetSets = 3,
                            targetKind = PlannedExerciseTargetKind.REPS,
                            repMin = 8,
                            repMax = 12,
                            durationMinSeconds = null,
                            durationMaxSeconds = null,
                            restSeconds = 90,
                            isOptional = false,
                        ),
                    ),
            )
        val result = createTrainingPlan(command)
        check(result is DomainResult.Success) { "Expected plan creation to succeed but was $result" }
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `starts loading then shows the empty state when there are no plans`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                assertEquals(TrainingPlanListContent.Loading, awaitItem().content)
                assertEquals(TrainingPlanListContent.Empty, awaitItem().content)
            }
        }

    @Test
    fun `shows the seeded plans as content with their planned exercise count`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitItem() // Loading
                val loaded = awaitItem()
                val content = loaded.content as TrainingPlanListContent.Content
                assertEquals(listOf("Push Pull Legs"), content.items.map { it.name })
                assertEquals(1, content.items.single().plannedExerciseCount)
            }
        }

    @Test
    fun `onRetry resubscribes and still reflects current content`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // content with Push Pull Legs

                viewModel.onRetry()

                awaitItem() // Loading again after retry
                val recovered = awaitItem()
                val content = recovered.content as TrainingPlanListContent.Content
                assertEquals(listOf("Push Pull Legs"), content.items.map { it.name })
            }
        }
}
