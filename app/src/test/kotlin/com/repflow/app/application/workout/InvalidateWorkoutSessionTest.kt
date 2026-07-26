package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class InvalidateWorkoutSessionTest {
    private val startedAt = Instant.parse("2026-01-01T00:00:00Z")
    private val endedAt = Instant.parse("2026-01-01T01:00:00Z")
    private val invalidatedAt = Instant.parse("2026-01-02T00:00:00Z")
    private val clock = FixedClock(invalidatedAt)
    private val repository = InMemoryWorkoutRepository()
    private val invalidateWorkoutSession = InvalidateWorkoutSession(repository, clock)

    private suspend fun seedCompletedSession(id: String = "session-1"): WorkoutSessionId {
        val sessionId = WorkoutSessionId(id)
        val started = WorkoutSession.start(sessionId, null, startedAt)
        val completed =
            when (val result = started.complete(endedAt)) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
            }
        check(repository.insert(completed) is DomainResult.Success)
        return sessionId
    }

    @Test
    fun `invalidates a completed session`() =
        runTest {
            val sessionId = seedCompletedSession()

            val result = invalidateWorkoutSession(sessionId)

            assertTrue(result is DomainResult.Success)
            val stored = requireNotNull(repository.findById(sessionId))
            assertTrue(stored.isInvalidated)
            assertEquals(invalidatedAt, stored.invalidatedAt)
        }

    @Test
    fun `excludes the invalidated session from observeCompletedSessions by default`() =
        runTest {
            val sessionId = seedCompletedSession()

            invalidateWorkoutSession(sessionId)

            assertEquals(
                emptyList<WorkoutSession>(),
                repository.observeCompletedSessions(includeInvalidated = false).first(),
            )
        }

    @Test
    fun `includes the invalidated session from observeCompletedSessions when explicitly requested`() =
        runTest {
            val sessionId = seedCompletedSession()

            invalidateWorkoutSession(sessionId)

            assertEquals(
                listOf(sessionId),
                repository.observeCompletedSessions(includeInvalidated = true).first().map { it.id },
            )
        }

    @Test
    fun `fails with AlreadyInvalidated for an already-invalidated session`() =
        runTest {
            val sessionId = seedCompletedSession()
            check(invalidateWorkoutSession(sessionId) is DomainResult.Success)

            val result = invalidateWorkoutSession(sessionId)

            assertEquals(DomainResult.Failure(WorkoutOperationError.AlreadyInvalidated), result)
        }

    @Test
    fun `fails with NotFound for a missing id`() =
        runTest {
            val result = invalidateWorkoutSession(WorkoutSessionId("missing"))

            assertEquals(DomainResult.Failure(WorkoutOperationError.NotFound), result)
        }
}
