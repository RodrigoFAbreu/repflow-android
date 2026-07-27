package com.repflow.app.application.history

import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ObserveWorkoutHistoryTest {
    private val repository = InMemoryWorkoutRepository()
    private val useCase = ObserveWorkoutHistory(repository)

    private fun exercise(id: String) =
        (
            WorkoutExercise.create(
                id = WorkoutExerciseId(id),
                sessionId = WorkoutSessionId("session"),
                exerciseId = ExerciseId("ex-1"),
                order = 0,
                exerciseNameSnapshot = "Bench Press",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                plannedExerciseId = null,
            ) as DomainResult.Success
        ).value

    private fun completedSession(
        id: String,
        startedAt: Instant,
        endedAt: Instant,
    ) = (
        (
            WorkoutSession
                .start(WorkoutSessionId(id), null, startedAt)
                .withAddedExercise(exercise("we-$id")) as DomainResult.Success
        ).value
            .complete(endedAt) as DomainResult.Success
    ).value

    @Test
    fun `emits only completed sessions, most recently ended first`() =
        runTest {
            val active = WorkoutSession.start(WorkoutSessionId("active"), null, Instant.parse("2026-01-01T00:00:00Z"))
            val older = completedSession("older", Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T01:00:00Z"))
            val newer = completedSession("newer", Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-02T01:00:00Z"))

            // Insert the completed sessions first (no active-session guard conflict), then
            // the active one last, so the fake's "one active session" insert guard never fires.
            repository.insert(older)
            repository.insert(newer)
            repository.insert(active)

            val history = useCase().first()
            assertEquals(listOf(WorkoutSessionId("newer"), WorkoutSessionId("older")), history.map { it.id })
        }
}
