package com.repflow.app.application.workout

import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
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
    private val editSet = EditWorkoutSet(repository, FixedClock(now))
    private val later = now.plusSeconds(60)
    private val editSetLater = EditWorkoutSet(repository, FixedClock(later))

    private suspend fun activeSessionId() = repository.findActiveSession()!!.id

    private suspend fun seedExerciseWithOneSet(
        rpe: Double? = null,
        pain: Int? = null,
        technique: Int? = null,
        isWarmup: Boolean = false,
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
                isWarmup = isWarmup,
                pain = pain,
                techniqueQuality = technique,
            ),
        )
        return sessionId to exerciseId
    }

    /** Three sets a test can tell apart: 60 x 8 at RPE 6, then 62.5 x 7 and 65 x 6 at RPE 7. */
    private suspend fun seedExerciseWithThreeSets(firstIsWarmup: Boolean = false): Pair<WorkoutSessionId, WorkoutExerciseId> {
        val (sessionId, exerciseId) = seedExerciseWithOneSet(rpe = 6.0, pain = 1, technique = 3, isWarmup = firstIsWarmup)
        listOf(62.5 to 7, 65.0 to 6).forEach { (load, reps) ->
            recordSet(
                RecordWorkoutSetCommand(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    load = load,
                    reps = reps,
                    durationSeconds = null,
                    rpe = 7.0,
                    isWarmup = false,
                    pain = 0,
                    techniqueQuality = 4,
                ),
            )
        }
        return sessionId to exerciseId
    }

    private suspend fun storedSets(sessionId: WorkoutSessionId) =
        repository
            .findById(sessionId)!!
            .exercises
            .single()
            .sets

    private suspend fun storedSet(sessionId: WorkoutSessionId) = storedSets(sessionId).single()

    @Suppress("LongParameterList")
    private fun correction(
        sessionId: WorkoutSessionId,
        exerciseId: WorkoutExerciseId,
        setId: WorkoutSetId,
        rpe: Double? = null,
        pain: Int? = null,
        technique: Int? = null,
        load: Double = 60.0,
        reps: Int = 8,
        isWarmup: Boolean = false,
    ) = EditWorkoutSetCommand(
        sessionId = sessionId,
        exerciseId = exerciseId,
        setId = setId,
        load = load,
        reps = reps,
        durationSeconds = null,
        rpe = rpe,
        isWarmup = isWarmup,
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
    fun `edit replaces the named set's values in place`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithOneSet()

            val result =
                editSet(
                    EditWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        setId = storedSet(sessionId).id,
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

            val result = editSetLater(correction(sessionId, exerciseId, logged.id, rpe = 8.0, pain = 0, technique = 4))

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

            val result = editSet(correction(sessionId, exerciseId, storedSet(sessionId).id, rpe = null, pain = null, technique = null))

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
                    correction(sessionId, exerciseId, logged.id, rpe = 10.5, pain = 0, technique = 4) to
                        WorkoutValidationError.RpeOutOfRange,
                    correction(sessionId, exerciseId, logged.id, rpe = 8.0, pain = 6, technique = 4) to
                        WorkoutValidationError.PainOutOfRange,
                    correction(sessionId, exerciseId, logged.id, rpe = 8.0, pain = 0, technique = 6) to
                        WorkoutValidationError.TechniqueQualityOutOfRange,
                )

            for ((command, error) in outOfRange) {
                assertEquals(
                    DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(error))),
                    editSet(command),
                )
                assertEquals(logged, storedSet(sessionId))
            }
        }

    @Test
    fun `editing the first of three sets changes it and stores the other two as they were`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets()
            val (first, second, third) = storedSets(sessionId)

            val result =
                editSetLater(correction(sessionId, exerciseId, first.id, rpe = 9.0, pain = 2, technique = 5, load = 70.0, reps = 5))

            assertTrue(result is DomainResult.Success)
            val (edited, storedSecond, storedThird) = storedSets(sessionId)
            assertEquals(70.0, edited.load)
            assertEquals(5, edited.reps)
            assertEquals(9.0, edited.rpe)
            assertEquals(2, edited.pain)
            assertEquals(5, edited.techniqueQuality)
            assertEquals(first.id, edited.id)
            assertEquals(first.order, edited.order)
            assertEquals(first.createdAt, edited.createdAt)
            assertEquals(later, edited.updatedAt)
            assertEquals(second, storedSecond)
            assertEquals(third, storedThird)
        }

    @Test
    fun `editing the middle set leaves the most recent set as it was`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets()
            val (first, second, third) = storedSets(sessionId)

            val result = editSetLater(correction(sessionId, exerciseId, second.id, load = 70.0, reps = 5))

            assertTrue(result is DomainResult.Success)
            val (storedFirst, edited, storedThird) = storedSets(sessionId)
            assertEquals(70.0, edited.load)
            assertEquals(second.id, edited.id)
            assertEquals(first, storedFirst)
            assertEquals(third, storedThird)
        }

    @Test
    fun `a warm-up set keeps the flag the command says, and a command clearing it clears only that set`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets(firstIsWarmup = true)
            val (first, second, third) = storedSets(sessionId)

            editSet(correction(sessionId, exerciseId, first.id, load = 40.0, isWarmup = true))

            assertEquals(true, storedSets(sessionId).first().isWarmup)
            assertEquals(40.0, storedSets(sessionId).first().load)

            editSet(correction(sessionId, exerciseId, first.id, load = 40.0, isWarmup = false))

            val (cleared, storedSecond, storedThird) = storedSets(sessionId)
            assertEquals(false, cleared.isWarmup)
            assertEquals(second, storedSecond)
            assertEquals(third, storedThird)
        }

    @Test
    fun `a set id not in the exercise fails with NotFound and writes nothing`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets()
            val otherExerciseId =
                (
                    addExercise(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = ExerciseId("exercise-2"),
                            exerciseNameSnapshot = "Bench",
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            plannedExerciseId = null,
                        ),
                    ) as DomainResult.Success
                ).value
            recordSet(
                RecordWorkoutSetCommand(
                    sessionId = sessionId,
                    exerciseId = otherExerciseId,
                    load = 40.0,
                    reps = 10,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                ),
            )
            val before = repository.findById(sessionId)!!
            val otherExercisesSet =
                before.exercises
                    .single { it.id == otherExerciseId }
                    .sets
                    .single()

            for (id in listOf(WorkoutSetId("missing"), otherExercisesSet.id)) {
                assertEquals(
                    DomainResult.Failure(WorkoutOperationError.NotFound),
                    editSetLater(correction(sessionId, exerciseId, id, load = 70.0, reps = 5)),
                )
                assertEquals(before, repository.findById(sessionId))
            }
        }

    @Test
    fun `a value out of range for the chosen set fails and leaves every set as it was`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets()
            val logged = storedSets(sessionId)

            assertEquals(
                DomainResult.Failure(WorkoutOperationError.ValidationFailed(listOf(WorkoutValidationError.RpeOutOfRange))),
                editSetLater(correction(sessionId, exerciseId, logged.first().id, rpe = 10.5, load = 70.0, reps = 5)),
            )
            assertEquals(logged, storedSets(sessionId))
        }

    @Test
    fun `undo after correcting the first set removes the third and keeps the correction`() =
        runTest {
            val (sessionId, exerciseId) = seedExerciseWithThreeSets()
            val (first, second, _) = storedSets(sessionId)
            editSetLater(correction(sessionId, exerciseId, first.id, load = 70.0, reps = 5))
            val corrected = storedSets(sessionId).first()

            val result = undoLastSet(sessionId, exerciseId)

            assertTrue(result is DomainResult.Success)
            assertEquals(70.0, corrected.load)
            assertEquals(listOf(corrected, second), storedSets(sessionId))
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
