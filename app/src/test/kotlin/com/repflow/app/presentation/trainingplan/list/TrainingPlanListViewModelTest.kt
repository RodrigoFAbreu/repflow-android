package com.repflow.app.presentation.trainingplan.list

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
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
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    private val workoutRepository = InMemoryWorkoutRepository()
    private val viewModel by lazy {
        TrainingPlanListViewModel(
            ObserveTrainingPlans(planRepository),
            ArchiveTrainingPlan(planRepository, clock),
            RestoreTrainingPlan(planRepository, clock),
            StartWorkoutSessionFromPlan(
                workoutRepository,
                GetExercise(exerciseRepository),
                clock,
                SequentialIdentifierGenerator(prefix = "session"),
            ),
        )
    }

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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            viewModel.uiState.test {
                assertEquals(TrainingPlanListContent.Loading, awaitItem().content)
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS), awaitItem().content)
            }
        }

    @Test
    fun `shows the seeded plans as content with their planned exercise count`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
    fun `a card carries the latest version number and an archived plan its archive instant`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Active Plan", exerciseId)
            val archivedPlanId = seedPlan("Retired Plan", exerciseId)
            archivePlan(archivedPlanId)

            viewModel.uiState.test {
                val active = awaitUntilFilterMatches(TrainingPlanStatusFilter.ACTIVE).content as TrainingPlanListContent.Content
                assertEquals(1, active.items.single().versionNumber)
                assertNull(active.items.single().archivedAt)

                viewModel.onFilterChanged(TrainingPlanStatusFilter.ARCHIVED)
                val archived = awaitUntilFilterMatches(TrainingPlanStatusFilter.ARCHIVED).content as TrainingPlanListContent.Content
                assertEquals(now, archived.items.single().archivedAt)
            }
        }

    @Test
    fun `starting a plan starts a session from its latest version and asks to open the workout`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)
            val versionId = requireNotNull(planRepository.findOverviewByPlanId(planId)).latestVersion.id

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onStartClicked(planId)
                awaitUntil { it.openWorkout }

                val session = requireNotNull(workoutRepository.findActiveSession())
                assertEquals(versionId, session.trainingPlanVersionId)
                assertEquals(listOf(exerciseId), session.exercises.map { it.exerciseId })

                viewModel.onWorkoutOpened()
                assertFalse(awaitItem().openWorkout)
            }
        }

    @Test
    fun `starting a plan while a workout is running reports it and opens nothing`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val firstPlan = seedPlan("Push Pull Legs", exerciseId)
            val secondPlan = seedPlan("Upper Lower", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()
                viewModel.onStartClicked(firstPlan)
                awaitUntil { it.openWorkout }
                viewModel.onWorkoutOpened()
                awaitUntil { !it.openWorkout }
                val runningSession = requireNotNull(workoutRepository.findActiveSession()).id

                viewModel.onStartClicked(secondPlan)
                val state = awaitUntilMessagesNotEmpty()

                assertTrue(state.messages.single() is TrainingPlanListMessage.WorkoutAlreadyActive)
                assertFalse(state.openWorkout)
                assertEquals(runningSession, workoutRepository.findActiveSession()?.id)
            }
        }

    @Test
    fun `onRetry resubscribes and still reflects current content`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onRetry()

                val recovered = awaitUntilContent()
                val content = recovered.content as TrainingPlanListContent.Content
                assertEquals(listOf("Push Pull Legs"), content.items.map { it.name })
            }
        }

    @Test
    fun `onFilterChanged switches between active and archived plans`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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

                val emptied = awaitUntilEmptyContent(from = state)
                assertEquals(TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS), emptied.content)
            }
        }

    @Test
    fun `onMessageShown removes only the given message id and preserves a newer one`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val planId = seedPlan("Push Pull Legs", exerciseId)

            viewModel.uiState.test {
                awaitUntilContent()

                viewModel.onArchiveClicked(planId)
                val archived = awaitUntilMessagesNotEmpty()
                awaitUntilEmptyContent(from = archived)

                viewModel.onRestoreClicked(planId)
                val restored = awaitUntilContent()
                assertEquals(listOf("Push Pull Legs"), (restored.content as TrainingPlanListContent.Content).items.map { it.name })
            }
        }

    @Test
    fun `undo archive is idempotent when the plan is already active`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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

    /**
     * Consumes items until [predicate] holds, starting from [from] if given
     * rather than always requiring a fresh [awaitItem]. `uiState` combines
     * two independently-updated `MutableStateFlow`s (content, messages), so
     * a single combine tick can satisfy more than one caller's condition at
     * once (e.g. an archive's content-becomes-empty and its message-enqueue
     * landing in the same emission); a caller that discards [from] and
     * blindly awaits a *new* item after a previous `awaitUntil*` call can
     * already-satisfied that new call's own condition would hang forever
     * waiting for an emission that will never come.
     */
    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntil(
        from: TrainingPlanListUiState? = null,
        predicate: (TrainingPlanListUiState) -> Boolean,
    ): TrainingPlanListUiState {
        var state = from ?: awaitItem()
        while (!predicate(state)) {
            state = awaitItem()
        }
        return state
    }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilContent(
        from: TrainingPlanListUiState? = null,
    ): TrainingPlanListUiState = awaitUntil(from) { it.content is TrainingPlanListContent.Content }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilEmptyContent(
        from: TrainingPlanListUiState? = null,
    ): TrainingPlanListUiState = awaitUntil(from) { it.content is TrainingPlanListContent.Empty }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilMessagesNotEmpty(
        from: TrainingPlanListUiState? = null,
    ): TrainingPlanListUiState = awaitUntil(from) { it.messages.isNotEmpty() }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilMessageCountAtLeast(
        count: Int,
        from: TrainingPlanListUiState? = null,
    ): TrainingPlanListUiState = awaitUntil(from) { it.messages.size >= count }

    private suspend fun ReceiveTurbine<TrainingPlanListUiState>.awaitUntilFilterMatches(
        filter: TrainingPlanStatusFilter,
        from: TrainingPlanListUiState? = null,
    ): TrainingPlanListUiState = awaitUntil(from) { it.filter == filter && it.content !is TrainingPlanListContent.Loading }
}
