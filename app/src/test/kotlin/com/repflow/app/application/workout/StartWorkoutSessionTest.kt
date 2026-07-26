package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StartWorkoutSessionTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val repository = InMemoryWorkoutRepository()
    private val useCase = StartWorkoutSession(repository, FixedClock(now), SequentialIdentifierGenerator(prefix = "session"))

    @Test
    fun `starts a session with no training plan version`() =
        runTest {
            val result = useCase(StartWorkoutSessionCommand(trainingPlanVersionId = null))

            assertTrue(result is DomainResult.Success)
            assertEquals(null, repository.findActiveSession()?.trainingPlanVersionId)
        }

    @Test
    fun `starts a session referencing a training plan version`() =
        runTest {
            val versionId = TrainingPlanVersionId("version-1")

            useCase(StartWorkoutSessionCommand(trainingPlanVersionId = versionId))

            assertEquals(versionId, repository.findActiveSession()?.trainingPlanVersionId)
        }

    @Test
    fun `rejects starting a second session while one is active`() =
        runTest {
            useCase(StartWorkoutSessionCommand(trainingPlanVersionId = null))

            val result = useCase(StartWorkoutSessionCommand(trainingPlanVersionId = null))

            assertEquals(DomainResult.Failure(WorkoutOperationError.ActiveSessionAlreadyExists), result)
        }
}
