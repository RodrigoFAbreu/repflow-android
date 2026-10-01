package com.repflow.app.presentation.progress

import com.repflow.app.application.progress.ObserveExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val repository = InMemoryWorkoutRepository()
    private val bench = ExerciseId("bench")
    private val pullUps = ExerciseId("pull-ups")
    private val day0: Instant = Instant.parse("2026-05-05T08:00:00Z")
    private var nextId = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ProgressViewModel(ObserveExerciseProgress(repository))

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private suspend fun seed(
        day: Long,
        exerciseId: ExerciseId,
        type: ExerciseTrackingType,
    ) {
        val id = WorkoutSessionId("s${nextId++}")
        val startedAt = day0.plus(Duration.ofDays(day))
        val set =
            success(
                WorkoutSet.create(
                    id = WorkoutSetId("set${nextId++}"),
                    order = 0,
                    trackingType = type,
                    load = if (type.supportsLoad) 80.0 + day else null,
                    reps = 5,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                ),
            )
        val exercise =
            success(
                WorkoutExercise.create(
                    id = WorkoutExerciseId("e${nextId++}"),
                    sessionId = id,
                    exerciseId = exerciseId,
                    order = 0,
                    exerciseNameSnapshot = exerciseId.value,
                    trackingType = type,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ),
            )
        repository.insert(
            success(
                WorkoutSession.reconstruct(
                    id = id,
                    trainingPlanVersionId = null,
                    status = WorkoutSessionStatus.COMPLETED,
                    startedAt = startedAt,
                    endedAt = startedAt.plus(Duration.ofHours(1)),
                    exercises = listOf(exercise),
                ),
            ),
        )
    }

    @Test
    fun `with no history the tab is loaded and empty`() =
        runTest {
            val state = viewModel().uiState.value

            assertFalse(state.isLoading)
            assertNull(state.exercise)
        }

    @Test
    fun `opens on the most recently trained exercise, charting its top set`() =
        runTest {
            seed(0, bench, ExerciseTrackingType.WEIGHT_AND_REPS)
            seed(1, bench, ExerciseTrackingType.WEIGHT_AND_REPS)
            seed(2, pullUps, ExerciseTrackingType.REPS_ONLY)

            val state = viewModel().uiState.value

            assertEquals(listOf(pullUps, bench), state.exercises.map { it.exerciseId })
            assertEquals(pullUps, state.exercise?.exerciseId)
            assertEquals(ProgressMetric.TOP_SET, state.metric)
            assertTrue(state.showsLoadMetricsUnavailable)
        }

    @Test
    fun `a metric the exercise does not offer falls back to top set, and the choice returns with the exercise`() =
        runTest {
            seed(0, bench, ExerciseTrackingType.WEIGHT_AND_REPS)
            seed(1, bench, ExerciseTrackingType.WEIGHT_AND_REPS)
            seed(2, pullUps, ExerciseTrackingType.REPS_ONLY)
            val viewModel = viewModel()

            viewModel.onExerciseSelected(bench)
            viewModel.onMetricSelected(ProgressMetric.VOLUME)
            assertEquals(ProgressMetric.VOLUME, viewModel.uiState.value.metric)
            assertFalse(viewModel.uiState.value.showsLoadMetricsUnavailable)
            assertEquals(
                2,
                viewModel.uiState.value.series
                    ?.points
                    ?.size,
            )

            viewModel.onExerciseSelected(pullUps)
            assertEquals(ProgressMetric.TOP_SET, viewModel.uiState.value.metric)

            viewModel.onExerciseSelected(bench)
            assertEquals(ProgressMetric.VOLUME, viewModel.uiState.value.metric)
        }

    @Test
    fun `a newly finished workout reaches the open tab`() =
        runTest {
            seed(0, bench, ExerciseTrackingType.WEIGHT_AND_REPS)
            val viewModel = viewModel()
            assertFalse(
                viewModel.uiState.value.series!!
                    .hasTrend,
            )

            seed(1, bench, ExerciseTrackingType.WEIGHT_AND_REPS)

            assertTrue(
                viewModel.uiState.value.series!!
                    .hasTrend,
            )
        }
}
