package com.repflow.app.application.progress

import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import java.time.Duration
import java.time.Instant

/** Completed-session builders for the remediation-1-remediation-1 CP2 read-model tests. */
internal class ProgressFixtures {
    val bench = ExerciseId("bench")
    val squat = ExerciseId("squat")
    private var nextId = 0

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    @Suppress("LongParameterList")
    fun set(
        type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        load: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
        rpe: Double? = null,
        warmup: Boolean = false,
    ): WorkoutSet {
        val id = nextId++
        return success(
            WorkoutSet.create(
                id = WorkoutSetId("set-$id"),
                order = id,
                trackingType = type,
                load = load,
                reps = reps,
                durationSeconds = seconds,
                rpe = rpe,
                isWarmup = warmup,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    fun loaded(
        load: Double,
        reps: Int,
        rpe: Double? = null,
        warmup: Boolean = false,
    ) = set(load = load, reps = reps, rpe = rpe, warmup = warmup)

    class Entry(
        val exerciseId: ExerciseId,
        val sets: List<WorkoutSet>,
        val type: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        val name: String = exerciseId.value,
    )

    fun entry(
        exerciseId: ExerciseId,
        vararg sets: WorkoutSet,
    ) = Entry(exerciseId, sets.toList())

    fun session(
        startedAt: Instant,
        vararg entries: Entry,
        invalidated: Boolean = false,
    ): WorkoutSession {
        val id = WorkoutSessionId("session-${nextId++}")
        val endedAt = startedAt.plus(Duration.ofHours(1))
        val exercises =
            entries.mapIndexed { order, entry ->
                success(
                    WorkoutExercise.create(
                        id = WorkoutExerciseId("${id.value}-$order"),
                        sessionId = id,
                        exerciseId = entry.exerciseId,
                        order = order,
                        exerciseNameSnapshot = entry.name,
                        trackingType = entry.type,
                        plannedExerciseId = null,
                        sets = entry.sets,
                    ),
                )
            }
        return success(
            WorkoutSession.reconstruct(
                id = id,
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = endedAt,
                exercises = exercises,
                invalidatedAt = if (invalidated) endedAt else null,
            ),
        )
    }
}
