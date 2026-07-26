package com.repflow.app.presentation.workout

import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSession
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
class ActiveWorkoutViewModelTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "session")
    private val repository = InMemoryWorkoutRepository()
    private val viewModel =
        ActiveWorkoutViewModel(
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(repository),
            startWorkoutSession = StartWorkoutSession(repository, clock, ids),
            completeWorkoutSession = CompleteWorkoutSession(repository, clock),
            abandonWorkoutSession = AbandonWorkoutSession(repository, clock),
        )

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading then shows no active session when none exists`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                assertEquals(ActiveWorkoutContent.Loading, awaitItem().content)
                assertEquals(ActiveWorkoutContent.NoActiveSession, awaitItem().content)
            }
        }

    @Test
    fun `onStartWorkout transitions to an active session with zero exercises`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // NoActiveSession

                viewModel.onStartWorkout()

                val active = awaitItem().content as ActiveWorkoutContent.Active
                assertEquals(0, active.exerciseCount)
                assertEquals(0, active.setCount)
            }
        }

    @Test
    fun `onCompleteWorkout returns to no active session`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            viewModel.uiState.test {
                awaitItem() // Loading
                awaitItem() // NoActiveSession

                viewModel.onStartWorkout()
                val active = awaitItem().content as ActiveWorkoutContent.Active

                viewModel.onCompleteWorkout(active.sessionId)

                assertEquals(ActiveWorkoutContent.NoActiveSession, awaitItem().content)
            }
        }
}
