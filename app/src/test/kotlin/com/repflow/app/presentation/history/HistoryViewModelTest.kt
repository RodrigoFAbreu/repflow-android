package com.repflow.app.presentation.history

import app.cash.turbine.test
import com.repflow.app.application.history.ObserveWorkoutHistory
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
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
            val viewModel = HistoryViewModel(ObserveWorkoutHistory(workoutRepository))

            viewModel.uiState.test {
                val loaded = awaitItem()
                assertEquals(false, loaded.isLoading)
                assertEquals(listOf("session-1"), loaded.sessions.map { it.id.value })
            }
        }

    @Test
    fun `onSessionClick selects the session and onDetailDismissed clears it`() =
        runTest {
            val sessionId = seedCompletedSession("session-1")
            val viewModel = HistoryViewModel(ObserveWorkoutHistory(workoutRepository))

            viewModel.uiState.test {
                awaitItem() // loaded

                viewModel.onSessionClick(sessionId)
                assertEquals(sessionId, awaitItem().selectedSessionId)

                viewModel.onDetailDismissed()
                assertNull(awaitItem().selectedSessionId)
            }
        }
}
