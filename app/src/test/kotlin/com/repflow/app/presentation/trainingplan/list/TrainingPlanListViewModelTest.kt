package com.repflow.app.presentation.trainingplan.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.ArchiveTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.trainingplan.RestoreTrainingPlan
import com.repflow.app.application.trainingplan.TrainingPlanPersistenceError
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val viewModel =
        TrainingPlanListViewModel(
            ObserveTrainingPlans(planRepository),
            ArchiveTrainingPlan(planRepository, clock),
            RestoreTrainingPlan(planRepository, clock),
        )

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
    ): TrainingPlanId {
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
        return result.value
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
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS), awaitItem().content)
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

    @Test
    fun `onFilterChanged switches between active and archived plans`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Active Plan", exerciseId)
            val archivedPlanId = seedPlan("Retired Plan", exerciseId)
            archivePlan(archivedPlanId)

            viewModel.uiState.test {
                var state = awaitUntilFilterMatches(TrainingPlanStatusFilter.ACTIVE)
                val activeContent = state.content as TrainingPlanListContent.Content
                assertEquals(listOf("Active Plan"), activeContent.items.map { it.name })

                viewModel.onFilterChanged(TrainingPlanStatusFilter.ARCHIVED)
                state = awaitUntilFilterMatches(TrainingPlanStatusFilter.ARCHIVED)
                val archivedContent = state.content as TrainingPlanListContent.Content
                assertEquals(listOf("Retired Plan"), archivedContent.items.map { it.name })
            }
        }

    @Test
    fun `an empty archived filter reports NO_ARCHIVED rather than NO_PLANS`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Active Plan", exerciseId)

            viewModel.uiState.test {
                awaitUntilFilterMatches(TrainingPlanStatusFilter.ACTIVE)

                viewModel.onFilterChanged(TrainingPlanStatusFilter.ARCHIVED)
                val archived = awaitUntilFilterMatches(TrainingPlanStatusFilter.ARCHIVED)
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_ARCHIVED), archived.content)
            }
        }

    @Test
    fun `archiving queues an Archived message and removes the plan from the active list`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent() // active content with Push Pull Legs

                viewModel.onArchiveClicked(planId)
                val state = awaitUntilMessagesNotEmpty()

                assertEquals(1, state.messages.size)
                val message = state.messages.single()
                assertTrue(message is TrainingPlanListMessage.Archived)
                assertEquals(planId, (message as TrainingPlanListMessage.Archived).planId)

                val emptied = awaitUntilEmptyContent()
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS), emptied.content)
            }
        }

    @Test
    fun `onMessageShown removes only the given message id and preserves a newer one`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId1 = seedPlan("Plan One", exerciseId)
            val planId2 = seedPlan("Plan Two", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onArchiveClicked(planId1)
                var state = awaitUntilMessagesNotEmpty()
                val firstId = state.messages.single().id

                viewModel.onArchiveClicked(planId2)
                state = awaitUntilMessageCountAtLeast(2)
                val secondId = state.messages.last().id

                viewModel.onMessageShown(firstId)
                var afterConsume = awaitItem()
                while (afterConsume.messages.any { it.id == firstId }) {
                    afterConsume = awaitItem()
                }
                assertEquals(listOf(secondId), afterConsume.messages.map { it.id })
            }
        }

    @Test
    fun `undo archive restores the plan via RestoreTrainingPlan`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onArchiveClicked(planId)
                awaitUntilMessagesNotEmpty()
                awaitUntilEmptyContent()

                viewModel.onRestoreClicked(planId)
                val restored = awaitUntilContent()
                assertEquals(listOf("Push Pull Legs"), (restored.content as TrainingPlanListContent.Content).items.map { it.name })
            }
        }

    @Test
    fun `undo archive is idempotent when the plan is already active`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()

                // Already active; restore should be a neutral no-op, not an error message.
                viewModel.onRestoreClicked(planId)
                expectNoEvents()
            }
        }

    @Test
    fun `a failed archive queues an OperationFailed message`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)
            planRepository.nextUpdatePlanFailure = TrainingPlanPersistenceError.Unavailable

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onArchiveClicked(planId)
                val state = awaitUntilMessagesNotEmpty()

                assertEquals(1, state.messages.size)
                assertTrue(state.messages.single() is TrainingPlanListMessage.OperationFailed)
            }
        }

    @Test
    fun `restoring from the archived filter moves the plan back to active`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)
            archivePlan(planId)

            viewModel.uiState.test {
                awaitUntilFilterMatches(TrainingPlanStatusFilter.ACTIVE) // active, empty (plan is archived)

                viewModel.onFilterChanged(TrainingPlanStatusFilter.ARCHIVED)
                val archivedList = awaitUntilFilterMatches(TrainingPlanStatusFilter.ARCHIVED)
                val archivedContent = archivedList.content as TrainingPlanListContent.Content
                assertEquals(listOf("Push Pull Legs"), archivedContent.items.map { it.name })

                viewModel.onRestoreClicked(planId)
                val afterRestore = awaitUntilEmptyContent()
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_ARCHIVED), afterRestore.content)
            }
        }

    private suspend fun archivePlan(planId: TrainingPlanId) {
        val overview = requireNotNull(planRepository.findOverviewByPlanId(planId))
        val archived = overview.plan.archive(now)
        check(planRepository.updatePlan(archived) is DomainResult.Success)
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilContent(): TrainingPlanListUiState {
        var state = awaitItem()
        while (state.content !is TrainingPlanListContent.Content) {
            state = awaitItem()
        }
        return state
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilEmptyContent(): TrainingPlanListUiState {
        var state = awaitItem()
        while (state.content !is TrainingPlanListContent.Empty) {
            state = awaitItem()
        }
        return state
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilMessagesNotEmpty(): TrainingPlanListUiState {
        var state = awaitItem()
        while (state.messages.isEmpty()) {
            state = awaitItem()
        }
        return state
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilMessageCountAtLeast(count: Int): TrainingPlanListUiState {
        var state = awaitItem()
        while (state.messages.size < count) {
            state = awaitItem()
        }
        return state
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilFilterMatches(
        filter: TrainingPlanStatusFilter,
    ): TrainingPlanListUiState {
        var state = awaitItem()
        while (state.filter != filter || state.content is TrainingPlanListContent.Loading) {
            state = awaitItem()
        }
        return state
    }
}
