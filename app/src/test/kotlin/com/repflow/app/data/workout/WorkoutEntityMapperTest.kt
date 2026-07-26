package com.repflow.app.data.workout

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WorkoutEntityMapperTest {
    private val startedAt: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private fun requireSuccess(result: DomainResult<*, *>): Any =
        when (result) {
            is DomainResult.Success -> requireNotNull(result.value)
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `round-trips a session with an exercise and a set through entities`() {
        val set =
            requireSuccess(
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = 8.5,
                    isWarmup = false,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                ),
            ) as WorkoutSet

        val exercise =
            requireSuccess(
                WorkoutExercise.create(
                    id = WorkoutExerciseId("we-1"),
                    sessionId = WorkoutSessionId("session-1"),
                    exerciseId = ExerciseId("exercise-1"),
                    order = 0,
                    exerciseNameSnapshot = "Back Squat",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ),
            ) as WorkoutExercise

        val session = WorkoutSession.start(WorkoutSessionId("session-1"), null, startedAt)
        val sessionWithExercise = requireSuccess(session.withAddedExercise(exercise)) as WorkoutSession

        val sessionEntity = WorkoutEntityMapper.toSessionEntity(sessionWithExercise)
        val exerciseEntities = WorkoutEntityMapper.toExerciseEntities(sessionWithExercise)
        val setEntities = WorkoutEntityMapper.toSetEntities(sessionWithExercise)

        val rebuilt =
            requireSuccess(
                WorkoutEntityMapper.toDomain(
                    sessionEntity,
                    exerciseEntities,
                    setEntities.groupBy { it.workoutExerciseId },
                ),
            ) as WorkoutSession

        assertEquals(sessionWithExercise, rebuilt)
    }
}
