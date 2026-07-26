package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class UndoAndEditLastWorkoutSetTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val repository = InMemoryWorkoutRepository()
    private val ids = SequentialIdentifierGenerator(prefix = "id")
    private val startSession = StartWorkoutSession(repository, FixedClock(now), ids)
    private val addExercise = AddWorkoutExercise(repository, ids)
    private val recordSet = RecordWorkoutSet(repository, FixedClock(now), ids)
    private val undoLastSet = UndoLastWorkoutSet(repository)
    private val editLastSet = EditLastWorkoutSet(repository, FixedClock(now))

    private suspend fun activeSessionId() = repository.findActiveSession()!!.id

    private suspend fun seedExerciseWithOneSet(): Pair<WorkoutSessionId, WorkoutExerciseId> {
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
        return sessionId to exerciseId
    }

    @Test
    fun `undo removes the most recently recorded set`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet()

            val result = undoLastSet(sessionId, exerciseId)

            assertTrue(result is DomainResult.Success)
            assertEquals(
                0,
                repository
                    .findById(sessionId)
                    ?.exercises
                    ?.first()
                    ?.sets
                    ?.size,
            )
        }

    @Test
    fun `edit replaces the most recently recorded set's values in place`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet()

            val result =
                editLastSet(
                    EditLastWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        load = 70.0,
                        reps = 5,
                        durationSeconds = null,
                        rpe = 9.0,
                        isWarmup = false,
                    ),
                )

            assertTrue(result is DomainResult.Success)
            val sets =
                repository
                    .findById(sessionId)
                    ?.exercises
                    ?.first()
                    ?.sets
            assertEquals(1, sets?.size)
            assertEquals(70.0, sets?.first()?.load)
            assertEquals(5, sets?.first()?.reps)
        }

    @Test
    fun `undo on an exercise with no sets fails with NotFound`() =
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

            val result = undoLastSet(sessionId, exerciseId)

            assertEquals(DomainResult.Failure(WorkoutOperationError.NotFound), result)
        }
}
