package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AddWorkoutExerciseAndRecordWorkoutSetTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val repository = InMemoryWorkoutRepository()
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val startSession = StartWorkoutSession(repository, FixedClock(now), ids)
    private val addExercise = AddWorkoutExercise(repository, ids)
    private val recordSet = RecordWorkoutSet(repository, FixedClock(now), ids)
    private val complete = CompleteWorkoutSession(repository, FixedClock(now.plusSeconds(3600)))
    private val abandon = AbandonWorkoutSession(repository, FixedClock(now.plusSeconds(3600)))

    private suspend fun activeSessionId() = repository.findActiveSession()!!.id

    @Test
    fun `adds an ad hoc exercise to the active session`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()

            val result =
                addExercise(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = ExerciseId("exercise-1"),
                        exerciseNameSnapshot = "Back Squat",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        plannedExerciseId = null,
                    ),
                )

            assertTrue(result is DomainResult.Success)
            assertEquals(1, repository.findById(sessionId)?.exercises?.size)
        }

    @Test
    fun `records a set against an existing exercise`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            val exerciseId =
                (
                    addExercise(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = ExerciseId("exercise-1"),
                            exerciseNameSnapshot = "Back Squat",
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            plannedExerciseId = null,
                        ),
                    ) as DomainResult.Success
                ).value

            val result =
                recordSet(
                    RecordWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = null,
                        isWarmup = false,
                    ),
                )

            assertTrue(result is DomainResult.Success)
            assertEquals(
                1,
                repository
                    .findById(sessionId)
                    ?.exercises
                    ?.first()
                    ?.sets
                    ?.size,
            )
        }

    @Test
    fun `recording a set for a missing exercise fails with NotFound`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()

            val result =
                recordSet(
                    RecordWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId =
                            com.repflow.app.domain.workout
                                .WorkoutExerciseId("missing"),
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = null,
                        isWarmup = false,
                    ),
                )

            assertEquals(DomainResult.Failure(WorkoutOperationError.NotFound), result)
        }

    @Test
    fun `completing a session prevents further set recording`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()
            val exerciseId =
                (
                    addExercise(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = ExerciseId("exercise-1"),
                            exerciseNameSnapshot = "Back Squat",
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            plannedExerciseId = null,
                        ),
                    ) as DomainResult.Success
                ).value

            complete(sessionId)

            val result =
                recordSet(
                    RecordWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        load = 60.0,
                        reps = 8,
                        durationSeconds = null,
                        rpe = null,
                        isWarmup = false,
                    ),
                )

            assertEquals(
                DomainResult.Failure(
                    WorkoutOperationError.ValidationFailed(listOf(com.repflow.app.domain.workout.WorkoutValidationError.SessionNotActive)),
                ),
                result,
            )
        }

    @Test
    fun `abandon marks the session abandoned and clears the active session`() =
        runTest {
            startSession(StartWorkoutSessionCommand(trainingPlanVersionId = null))
            val sessionId = activeSessionId()

            abandon(sessionId)

            assertEquals(null, repository.findActiveSession())
            assertEquals(
                com.repflow.app.domain.workout.WorkoutSessionStatus.ABANDONED,
                repository.findById(sessionId)?.status,
            )
        }
}
