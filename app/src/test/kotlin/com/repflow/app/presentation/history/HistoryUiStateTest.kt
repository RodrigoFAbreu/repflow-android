package com.repflow.app.presentation.history

import com.repflow.app.application.trainingplan.TrainingPlanVersionLabel
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HistoryUiStateTest {
    private fun session(
        id: String,
        startedAt: Instant,
        exerciseId: String = "ex-1",
        trainingPlanVersionId: TrainingPlanVersionId? = null,
        invalidated: Boolean = false,
    ): WorkoutSession {
        val exercise =
            requireSuccess(
                WorkoutExercise.create(
                    id = WorkoutExerciseId("we-$id"),
                    sessionId = WorkoutSessionId(id),
                    exerciseId = ExerciseId(exerciseId),
                    order = 0,
                    exerciseNameSnapshot = "Exercise $exerciseId",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                ),
            )
        val started =
            requireSuccess(
                WorkoutSession
                    .start(WorkoutSessionId(id), trainingPlanVersionId, startedAt)
                    .withAddedExercise(exercise),
            )
        val completed = requireSuccess(started.complete(startedAt.plusSeconds(1800)))
        return if (invalidated) requireSuccess(completed.invalidate(startedAt.plusSeconds(3600))) else completed
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `visibleSessions hides invalidated sessions by default`() {
        val visible = session("1", Instant.parse("2026-01-01T00:00:00Z"))
        val invalidated = session("2", Instant.parse("2026-01-02T00:00:00Z"), invalidated = true)
        val state = HistoryUiState(isLoading = false, sessions = listOf(visible, invalidated))

        assertEquals(listOf("1"), state.visibleSessions.map { it.id.value })
    }

    @Test
    fun `visibleSessions reveals invalidated sessions when showInvalidated is true`() {
        val invalidated = session("1", Instant.parse("2026-01-01T00:00:00Z"), invalidated = true)
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(invalidated),
                filters = HistoryFilters(showInvalidated = true),
            )

        assertEquals(listOf("1"), state.visibleSessions.map { it.id.value })
    }

    @Test
    fun `visibleSessions filters by exercise`() {
        val squat = session("1", Instant.parse("2026-01-01T00:00:00Z"), exerciseId = "squat")
        val bench = session("2", Instant.parse("2026-01-02T00:00:00Z"), exerciseId = "bench")
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(squat, bench),
                filters = HistoryFilters(exerciseId = ExerciseId("bench")),
            )

        assertEquals(listOf("2"), state.visibleSessions.map { it.id.value })
    }

    @Test
    fun `visibleSessions filters ad-hoc-only sessions`() {
        val adHoc = session("1", Instant.parse("2026-01-01T00:00:00Z"))
        val fromPlan = session("2", Instant.parse("2026-01-02T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("v1"))
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(adHoc, fromPlan),
                filters = HistoryFilters(plan = HistoryPlanFilter.AdHocOnly),
            )

        assertEquals(listOf("1"), state.visibleSessions.map { it.id.value })
    }

    @Test
    fun `visibleSessions filters by plan identity across two of its versions`() {
        val planId = TrainingPlanId("plan-1")
        val fromV1 = session("1", Instant.parse("2026-01-01T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("v1"))
        val fromV2 = session("2", Instant.parse("2026-01-02T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("v2"))
        val fromOtherPlan =
            session("3", Instant.parse("2026-01-03T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("other-v1"))
        val versionLabels =
            mapOf(
                TrainingPlanVersionId("v1") to TrainingPlanVersionLabel(planId, "Push Day"),
                TrainingPlanVersionId("v2") to TrainingPlanVersionLabel(planId, "Push Day"),
                TrainingPlanVersionId("other-v1") to TrainingPlanVersionLabel(TrainingPlanId("plan-2"), "Leg Day"),
            )
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(fromV1, fromV2, fromOtherPlan),
                versionLabels = versionLabels,
                filters = HistoryFilters(plan = HistoryPlanFilter.Plan(planId, "Push Day")),
            )

        assertEquals(listOf("1", "2"), state.visibleSessions.sortedBy { it.id.value }.map { it.id.value })
    }

    @Test
    fun `visibleSessions filters by inclusive date range`() {
        val before = session("1", Instant.parse("2026-01-01T00:00:00Z"))
        val within = session("2", Instant.parse("2026-01-05T00:00:00Z"))
        val after = session("3", Instant.parse("2026-01-10T00:00:00Z"))
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(before, within, after),
                filters =
                    HistoryFilters(
                        startDate = java.time.LocalDate.of(2026, 1, 3),
                        endDate = java.time.LocalDate.of(2026, 1, 7),
                    ),
            )

        assertEquals(listOf("2"), state.visibleSessions.map { it.id.value })
    }

    @Test
    fun `visibleSessions sorts newest first by default and oldest first when selected`() {
        val earlier = session("1", Instant.parse("2026-01-01T00:00:00Z"))
        val later = session("2", Instant.parse("2026-01-05T00:00:00Z"))
        val newestFirst = HistoryUiState(isLoading = false, sessions = listOf(earlier, later))
        val oldestFirst =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(earlier, later),
                filters = HistoryFilters(sortOrder = HistorySortOrder.OLDEST_FIRST),
            )

        assertEquals(listOf("2", "1"), newestFirst.visibleSessions.map { it.id.value })
        assertEquals(listOf("1", "2"), oldestFirst.visibleSessions.map { it.id.value })
    }

    @Test
    fun `availableExerciseOptions lists distinct exercises sorted by name`() {
        val squat = session("1", Instant.parse("2026-01-01T00:00:00Z"), exerciseId = "squat")
        val bench = session("2", Instant.parse("2026-01-02T00:00:00Z"), exerciseId = "bench")
        val squatAgain = session("3", Instant.parse("2026-01-03T00:00:00Z"), exerciseId = "squat")
        val state = HistoryUiState(isLoading = false, sessions = listOf(squat, bench, squatAgain))

        assertEquals(listOf("bench", "squat"), state.availableExerciseOptions.map { it.id.value })
    }

    @Test
    fun `availablePlanOptions lists distinct plans by identity sorted by name`() {
        val planId = TrainingPlanId("plan-1")
        val fromV1 = session("1", Instant.parse("2026-01-01T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("v1"))
        val fromV2 = session("2", Instant.parse("2026-01-02T00:00:00Z"), trainingPlanVersionId = TrainingPlanVersionId("v2"))
        val adHoc = session("3", Instant.parse("2026-01-03T00:00:00Z"))
        val versionLabels =
            mapOf(
                TrainingPlanVersionId("v1") to TrainingPlanVersionLabel(planId, "Push Day"),
                TrainingPlanVersionId("v2") to TrainingPlanVersionLabel(planId, "Push Day"),
            )
        val state =
            HistoryUiState(
                isLoading = false,
                sessions = listOf(fromV1, fromV2, adHoc),
                versionLabels = versionLabels,
            )

        assertEquals(listOf(planId), state.availablePlanOptions.map { it.planId })
    }
}
