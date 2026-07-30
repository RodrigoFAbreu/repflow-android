package com.repflow.app.presentation.history

import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.history.ObserveWorkoutHistory
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.trainingplan.ReviseTrainingPlan
import com.repflow.app.application.trainingplan.ReviseTrainingPlanCommand
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.InvalidateWorkoutSession
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val workoutRepository = InMemoryWorkoutRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()
    private val clock = FixedClock(Instant.parse("2026-01-02T00:00:00Z"))

    private fun viewModel() =
        HistoryViewModel(
            ObserveWorkoutHistory(workoutRepository),
            ObserveTrainingPlanVersionLabels(trainingPlanRepository),
            InvalidateWorkoutSession(workoutRepository, clock),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    private suspend fun seedCompletedSession(id: String): WorkoutSessionId {
        val sessionId = WorkoutSessionId(id)
        val started =
            WorkoutSession.start(
                id = sessionId,
                trainingPlanVersionId = null,
                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        val completed =
            when (val result = started.complete(endedAt = Instant.parse("2026-01-01T01:00:00Z"))) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
            }
        val insertResult = workoutRepository.insert(completed)
        check(insertResult is DomainResult.Success) { "Expected insert to succeed but was $insertResult" }
        return sessionId
    }

    @Test
    fun `shows the seeded completed sessions`() =
        runTest {
            seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                val loaded = awaitItem()
                assertEquals(false, loaded.isLoading)
                assertEquals(listOf("session-1"), loaded.sessions.map { it.id.value })
            }
        }

    @Test
    fun `versionLabels refresh reactively when a plan is renamed while History stays open`() =
        runTest {
            // Milestone 8, implementation-review finding #5: versionLabels used to be a
            // one-shot suspend read taken once in HistoryViewModel.init, so a rename after
            // the screen was already open would never be reflected without recreating the
            // ViewModel. Reusing the real ReviseTrainingPlan use case (not a repository
            // shortcut) proves the fix against the actual rename path, not a synthetic one.
            val exerciseRepository = InMemoryExerciseRepository()
            val ids = SequentialIdentifierGenerator(prefix = "plan")
            val createTrainingPlan = CreateTrainingPlan(trainingPlanRepository, exerciseRepository, clock, ids)
            val reviseTrainingPlan = ReviseTrainingPlan(trainingPlanRepository, exerciseRepository, clock, ids)
            val exercise =
                requireSuccess(
                    Exercise.create(
                        id = ExerciseId("bench-press"),
                        name = requireSuccess(ExerciseName.create("Bench Press")),
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        instructions = null,
                        defaultLoadIncrement = null,
                        defaultRestDuration = null,
                        origin = ExerciseOrigin.CUSTOM,
                        createdAt = clock.now(),
                    ),
                )
            exerciseRepository.seed(exercise)
            val plannedExercise =
                PlannedExerciseInput(
                    exerciseId = exercise.id.value,
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
            seedCompletedSession("session-1")
            val planId = requireSuccess(createTrainingPlan(CreateTrainingPlanCommand("Push day", listOf(plannedExercise))))
            val versionId =
                requireNotNull(trainingPlanRepository.findOverviewByPlanId(planId)).latestVersion.id
            val viewModel = viewModel()

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.versionLabels[versionId]?.planName != "Push day") {
                    state = awaitItem()
                }

                requireSuccess(reviseTrainingPlan(ReviseTrainingPlanCommand(planId.value, "Push day (renamed)", listOf(plannedExercise))))

                var afterRename = awaitItem()
                while (afterRename.versionLabels[versionId]?.planName != "Push day (renamed)") {
                    afterRename = awaitItem()
                }
                assertEquals("Push day (renamed)", afterRename.versionLabels[versionId]?.planName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onSessionClick selects the session and onDetailDismissed clears it`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onSessionClick(sessionId)
                assertEquals(sessionId, awaitItem().selectedSessionId)

                viewModel.onDetailDismissed()
                assertNull(awaitItem().selectedSessionId)
            }
        }

    @Test
    fun `onInvalidateClicked removes the session from the default view and queues a message`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded with session-1

                viewModel.onInvalidateClicked(sessionId)
                var state = awaitItem()
                while (state.visibleSessions.isNotEmpty() || state.messages.isEmpty()) {
                    state = awaitItem()
                }
                assertEquals(emptyList<String>(), state.visibleSessions.map { it.id.value })
                // The row and its data are never deleted - it stays in the raw list.
                assertEquals(listOf("session-1"), state.sessions.map { it.id.value })
                assertEquals(1, state.messages.size)
                assertEquals(true, state.messages.single() is HistoryMessage.Invalidated)
            }
        }

    @Test
    fun `onShowInvalidatedChanged reveals an invalidated session again`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onInvalidateClicked(sessionId)
                var state = awaitItem()
                while (state.visibleSessions.isNotEmpty() || state.messages.isEmpty()) {
                    state = awaitItem()
                }

                viewModel.onShowInvalidatedChanged(true)
                val revealed = awaitItem()
                assertEquals(listOf("session-1"), revealed.visibleSessions.map { it.id.value })
                assertEquals(true, revealed.visibleSessions.single().isInvalidated)
            }
        }

    @Test
    fun `onInvalidateClicked dismisses an open detail view for the invalidated session`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onSessionClick(sessionId)
                assertEquals(sessionId, awaitItem().selectedSessionId)

                viewModel.onInvalidateClicked(sessionId)
                var state = awaitItem()
                while (state.selectedSessionId != null || state.messages.isEmpty()) {
                    state = awaitItem()
                }
                assertNull(state.selectedSessionId)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onInvalidateClicked on an already-invalidated session queues an operation-failed message`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onInvalidateClicked(sessionId)
                var state = awaitItem()
                while (state.messages.isEmpty()) {
                    state = awaitItem()
                }
                viewModel.onInvalidateClicked(sessionId)
                var failed = awaitItem()
                while (failed.messages.size < 2) {
                    failed = awaitItem()
                }
                assertEquals(true, failed.messages.last() is HistoryMessage.OperationFailed)
            }
        }

    @Test
    fun `onMessageShown dequeues the message`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = viewModel()

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onInvalidateClicked(sessionId)
                var state = awaitItem()
                while (state.messages.isEmpty()) {
                    state = awaitItem()
                }
                val messageId = state.messages.single().id

                viewModel.onMessageShown(messageId)
                var afterShown = awaitItem()
                while (afterShown.messages.isNotEmpty()) {
                    afterShown = awaitItem()
                }
                assertEquals(emptyList<HistoryMessage>(), afterShown.messages)
            }
        }
}
