package com.repflow.app.domain.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WorkoutSessionTest {
    private val startedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun exercise(order: Int = 0) =
        requireSuccess(
            WorkoutExercise.create(
                id = WorkoutExerciseId("we-$order"),
                sessionId = WorkoutSessionId("session-1"),
                exerciseId = ExerciseId("exercise-1"),
                order = order,
                exerciseNameSnapshot = "Back Squat",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                plannedExerciseId = null,
            ),
        )

    @Test
    fun `start creates an active session with no exercises`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)

        assertEquals(WorkoutSessionStatus.ACTIVE, session.status)
        assertEquals(emptyList<WorkoutExercise>(), session.exercises)
        assertEquals(null, session.endedAt)
    }

    @Test
    fun `reconstruct rejects endedAt before startedAt`() {
        val result =
            WorkoutSession.reconstruct(
                id = WorkoutSessionId("s1"),
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = startedAt.minusSeconds(1),
                exercises = emptyList(),
            )

        assertEquals(DomainResult.Failure(WorkoutValidationError.EndedBeforeStarted), result)
    }

    @Test
    fun `reconstruct rejects a terminal status with no endedAt`() {
        val result =
            WorkoutSession.reconstruct(
                id = WorkoutSessionId("s1"),
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.ABANDONED,
                startedAt = startedAt,
                endedAt = null,
                exercises = emptyList(),
            )

        assertEquals(DomainResult.Failure(WorkoutValidationError.EndedAtRequiredForTerminalStatus), result)
    }

    @Test
    fun `reconstruct rejects an active status carrying an endedAt`() {
        val result =
            WorkoutSession.reconstruct(
                id = WorkoutSessionId("s1"),
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.ACTIVE,
                startedAt = startedAt,
                endedAt = startedAt.plusSeconds(60),
                exercises = emptyList(),
            )

        assertEquals(DomainResult.Failure(WorkoutValidationError.EndedAtNotAllowedForActiveSession), result)
    }

    @Test
    fun `reconstruct rejects duplicate exercise order values`() {
        val result =
            WorkoutSession.reconstruct(
                id = WorkoutSessionId("s1"),
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.ACTIVE,
                startedAt = startedAt,
                endedAt = null,
                exercises = listOf(exercise(0), exercise(0)),
            )

        assertEquals(DomainResult.Failure(WorkoutValidationError.DuplicateExerciseOrder), result)
    }

    @Test
    fun `reconstruct preserves a fixed training plan version reference`() {
        val versionId = TrainingPlanVersionId("version-1")

        val session =
            requireSuccess(
                WorkoutSession.reconstruct(
                    id = WorkoutSessionId("s1"),
                    trainingPlanVersionId = versionId,
                    status = WorkoutSessionStatus.ACTIVE,
                    startedAt = startedAt,
                    endedAt = null,
                    exercises = emptyList(),
                ),
            )

        assertEquals(versionId, session.trainingPlanVersionId)
    }

    @Test
    fun `withAddedExercise rejects a non-active session`() {
        val completed =
            requireSuccess(
                WorkoutSession
                    .start(WorkoutSessionId("s1"), null, startedAt)
                    .complete(startedAt.plusSeconds(1)),
            )

        val result = completed.withAddedExercise(exercise())

        assertEquals(DomainResult.Failure(WorkoutValidationError.SessionNotActive), result)
    }

    @Test
    fun `withAddedExercise rejects a duplicate order`() {
        val session =
            requireSuccess(WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt).withAddedExercise(exercise(0)))

        val result = session.withAddedExercise(exercise(0))

        assertEquals(DomainResult.Failure(WorkoutValidationError.DuplicateExerciseOrder), result)
    }

    @Test
    fun `complete is terminal and rejects a second completion`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)
        val completed = requireSuccess(session.complete(startedAt.plusSeconds(30)))

        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status)
        assertEquals(
            DomainResult.Failure(WorkoutValidationError.SessionNotActive),
            completed.complete(startedAt.plusSeconds(60)),
        )
    }

    @Test
    fun `abandon rejects an endedAt before startedAt`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)

        val result = session.abandon(startedAt.minusSeconds(1))

        assertEquals(DomainResult.Failure(WorkoutValidationError.EndedBeforeStarted), result)
    }

    @Test
    fun `withStartedRestTimer sets the timer on an active session`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)
        val timer = RestTimer.start(90, startedAt)

        val updated = requireSuccess(session.withStartedRestTimer(timer))

        assertEquals(timer, updated.restTimer)
    }

    @Test
    fun `withClearedRestTimer removes the timer`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)
        val withTimer = requireSuccess(session.withStartedRestTimer(RestTimer.start(90, startedAt)))

        val cleared = requireSuccess(withTimer.withClearedRestTimer())

        assertEquals(null, cleared.restTimer)
    }

    @Test
    fun `completing a session clears any running rest timer`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)
        val withTimer = requireSuccess(session.withStartedRestTimer(RestTimer.start(90, startedAt)))

        val completed = requireSuccess(withTimer.complete(startedAt.plusSeconds(30)))

        assertEquals(null, completed.restTimer)
    }

    @Test
    fun `rest timer methods reject a non-active session`() {
        val session = WorkoutSession.start(WorkoutSessionId("s1"), null, startedAt)
        val completed = requireSuccess(session.complete(startedAt.plusSeconds(30)))

        assertEquals(
            DomainResult.Failure(WorkoutValidationError.SessionNotActive),
            completed.withStartedRestTimer(RestTimer.start(90, startedAt)),
        )
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
}
