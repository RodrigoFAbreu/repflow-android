package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RestTimerUseCasesTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val repository = InMemoryWorkoutRepository()
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val startSession = StartWorkoutSession(repository, FixedClock(now), ids)

    private suspend fun activeSessionId() = repository.findActiveSession()!!.id

    @Test
    fun `start rest timer persists a running timer`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            val start = StartRestTimer(repository, FixedClock(now))

            val result = start(sessionId, durationSeconds = 90)

            assertTrue(result is DomainResult.Success)
            val timer = repository.findById(sessionId)!!.restTimer!!
            assertEquals(now.plusSeconds(90), timer.endAt)
            assertEquals(90, timer.totalDurationSeconds)
        }

    @Test
    fun `adjust rest timer adds time`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            StartRestTimer(repository, FixedClock(now))(sessionId, durationSeconds = 90)
            val adjust = AdjustRestTimer(repository, FixedClock(now))

            adjust(sessionId, RestTimerAdjustment.ADD, 15)

            assertEquals(now.plusSeconds(105), repository.findById(sessionId)!!.restTimer!!.endAt)
        }

    @Test
    fun `adjust rest timer removes time`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            StartRestTimer(repository, FixedClock(now))(sessionId, durationSeconds = 90)
            val adjust = AdjustRestTimer(repository, FixedClock(now))

            adjust(sessionId, RestTimerAdjustment.REMOVE, 15)

            assertEquals(now.plusSeconds(75), repository.findById(sessionId)!!.restTimer!!.endAt)
        }

    @Test
    fun `adjust rest timer fails when no timer is running`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            val adjust = AdjustRestTimer(repository, FixedClock(now))

            val result = adjust(sessionId, RestTimerAdjustment.ADD, 15)

            assertEquals(DomainResult.Failure(WorkoutOperationError.NotFound), result)
        }

    @Test
    fun `skip rest timer clears it`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            StartRestTimer(repository, FixedClock(now))(sessionId, durationSeconds = 90)
            val skip = SkipRestTimer(repository)

            val result = skip(sessionId)

            assertTrue(result is DomainResult.Success)
            assertNull(repository.findById(sessionId)!!.restTimer)
        }
}
