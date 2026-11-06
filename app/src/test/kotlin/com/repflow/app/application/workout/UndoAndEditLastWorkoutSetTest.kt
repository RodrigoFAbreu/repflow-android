package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutValidationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private suspend fun seedExerciseWithOneSet(
        rpe: Double? = null,
        pain: Int? = null,
        technique: Int? = null,
    ): Pair<WorkoutSessionId, WorkoutExerciseId> {
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
                rpe = rpe,
                isWarmup = false,
                pain = pain,
                techniqueQuality = technique,
            ),
        )
        return sessionId to exerciseId
    }

    private suspend fun storedSet(sessionId: WorkoutSessionId) =
        repository
            .findById(sessionId)!!
            .exercises
            .single()
            .sets
            .single()

    private fun correction(
        sessionId: WorkoutSessionId,
        exerciseId: WorkoutExerciseId,
        rpe: Double?,
        pain: Int?,
        technique: Int?,
    ) = EditLastWorkoutSetCommand(
        sessionId = sessionId,
        exerciseId = exerciseId,
        load = 60.0,
        reps = 8,
        durationSeconds = null,
        rpe = rpe,
        isWarmup = false,
        pain = pain,
        techniqueQuality = technique,
    )

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
    fun `a corrected RPE and pain and technique are stored on the same set`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet(rpe = 6.0, pain = 1, technique = 3)
            val logged = storedSet(sessionId)
            val later = now.plusSeconds(60)

            val result =
                EditLastWorkoutSet(repository, FixedClock(later))(
                    correction(sessionId, exerciseId, rpe = 8.0, pain = 0, technique = 4),
                )

            assertTrue(result is DomainResult.Success)
            val stored = storedSet(sessionId)
            assertEquals(8.0, stored.rpe)
            assertEquals(0, stored.pain)
            assertEquals(4, stored.techniqueQuality)
            assertEquals(logged.id, stored.id)
            assertEquals(logged.order, stored.order)
            assertEquals(now, stored.createdAt)
            assertEquals(later, stored.updatedAt)
        }

    @Test
    fun `a correction that clears RPE and pain and technique stores null for all three`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet(rpe = 6.0, pain = 1, technique = 3)

            val result = editLastSet(correction(sessionId, exerciseId, rpe = null, pain = null, technique = null))

            assertTrue(result is DomainResult.Success)
            val stored = storedSet(sessionId)
            assertNull(stored.rpe)
            assertNull(stored.pain)
            assertNull(stored.techniqueQuality)
        }

    @Test
    fun `an out of range RPE or pain or technique fails and leaves the stored set as it was`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet(rpe = 6.0, pain = 1, technique = 3)
            val logged = storedSet(sessionId)

            val outOfRange =
                listOf(
                    correction(sessionId, exerciseId, rpe = 10.5, pain = 0, technique = 4) to WorkoutValidationError.RpeOutOfRange,
                    correction(sessionId, exerciseId, rpe = 8.0, pain = 6, technique = 4) to WorkoutValidationError.PainOutOfRange,
                    correction(sessionId, exerciseId, rpe = 8.0, pain = 0, technique = 6) to
                        WorkoutValidationError.TechniqueQualityOutOfRange,
                )

            for ((command, error) in outOfRange) {
                assertEquals(
                    DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error))),
                    editLastSet(command),
                )
                assertEquals(logged, storedSet(sessionId))
            }
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
