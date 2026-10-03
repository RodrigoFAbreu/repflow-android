package com.repflow.app.application.history

import app.cash.turbine.test
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Home's history read model (remediation-1 CP5): the last valid workout, its load increases, and the plan last trained. */
class ObserveRecentTrainingTest {
    private val squat = ExerciseId("squat")
    private val bench = ExerciseId("bench")
    private val plank = ExerciseId("plank")

    @Test
    fun `no completed workout means no summary and no plan`() {
        val recent = recentTrainingOf(emptyList())
        assertNull(recent.lastWorkout)
        assertNull(recent.lastTrainedPlanVersionId)
    }

    @Test
    fun `the last workout is the most recently ended valid one, and invalidated ones are skipped`() {
        val older = session("older", day = 1)
        val newer = session("newer", day = 3)
        val invalidated = session("invalidated", day = 5, invalidated = true)

        val recent = recentTrainingOf(listOf(older, invalidated, newer))

        assertEquals(newer.id, recent.lastWorkout?.session?.id)
    }

    @Test
    fun `an increase is a heavier top working set than the exercise's previous valid session`() {
        val earlier = session("earlier", day = 1, exercises = listOf(squat to listOf(100.0), bench to listOf(80.0)))
        val last = session("last", day = 3, exercises = listOf(squat to listOf(102.5), bench to listOf(80.0)))

        assertEquals(1, recentTrainingOf(listOf(earlier, last)).lastWorkout?.loadIncreases)
    }

    @Test
    fun `warm-ups, unloaded exercises and a first time on an exercise are never increases`() {
        val earlier = session("earlier", day = 1, exercises = listOf(squat to listOf(100.0)))
        val last =
            session(
                "last",
                day = 3,
                exercises =
                    listOf(
                        // Only the warm-up is heavier than last time.
                        squat to listOf(100.0),
                        bench to listOf(60.0),
                        plank to listOf(null),
                    ),
                warmupLoad = 140.0,
            )

        assertEquals(0, recentTrainingOf(listOf(earlier, last)).lastWorkout?.loadIncreases)
    }

    @Test
    fun `the comparison skips sessions without the exercise and invalidated ones`() {
        val oldest = session("oldest", day = 1, exercises = listOf(squat to listOf(90.0)))
        val invalidated = session("invalidated", day = 2, exercises = listOf(squat to listOf(120.0)), invalidated = true)
        val unrelated = session("unrelated", day = 3, exercises = listOf(bench to listOf(80.0)))
        val last = session("last", day = 4, exercises = listOf(squat to listOf(100.0)))

        assertEquals(1, recentTrainingOf(listOf(oldest, invalidated, unrelated, last)).lastWorkout?.loadIncreases)
    }

    @Test
    fun `the plan last trained is the newest valid session started from a plan`() {
        val fromPlan = session("from-plan", day = 1, planVersion = "version-a")
        val adHoc = session("ad-hoc", day = 2)

        assertEquals(TrainingPlanVersionId("version-a"), recentTrainingOf(listOf(fromPlan, adHoc)).lastTrainedPlanVersionId)
    }

    @Test
    fun `the flow re-derives when a workout completes`() =
        runTest {
            val repository = InMemoryWorkoutRepository()
            ObserveRecentTraining(repository)().test {
                assertNull(awaitItem().lastWorkout)

                val completed = session("first", day = 1)
                repository.insert(completed)

                assertEquals(completed.id, awaitItem().lastWorkout?.session?.id)
            }
        }

    @Suppress("LongParameterList")
    private fun session(
        id: String,
        day: Int,
        exercises: List<Pair<ExerciseId, List<Double?>>> = emptyList(),
        planVersion: String? = null,
        invalidated: Boolean = false,
        warmupLoad: Double? = null,
    ): WorkoutSession {
        val startedAt = Instant.parse("2026-08-0${day}T10:00:00Z")
        val endedAt = startedAt.plusSeconds(3_600)
        val sessionId = WorkoutSessionId(id)
        val workoutExercises =
            exercises.mapIndexed { index, (exerciseId, loads) ->
                val tracking = if (loads.all { it == null }) ExerciseTrackingType.DURATION else ExerciseTrackingType.WEIGHT_AND_REPS
                val working =
                    loads.mapIndexed { setIndex, load ->
                        workoutSet("$id-$index-$setIndex", setIndex, tracking, load, isWarmup = false, at = startedAt)
                    }
                val warmup =
                    if (warmupLoad != null && index == 0) {
                        listOf(workoutSet("$id-$index-w", working.size, tracking, warmupLoad, isWarmup = true, at = startedAt))
                    } else {
                        emptyList()
                    }
                success(
                    WorkoutExercise.create(
                        id = WorkoutExerciseId("$id-$index"),
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        order = index,
                        exerciseNameSnapshot = exerciseId.value,
                        trackingType = tracking,
                        plannedExerciseId = null,
                        sets = working + warmup,
                    ),
                )
            }
        return success(
            WorkoutSession.reconstruct(
                id = sessionId,
                trainingPlanVersionId = planVersion?.let(::TrainingPlanVersionId),
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = startedAt,
                endedAt = endedAt,
                exercises = workoutExercises,
                invalidatedAt = if (invalidated) endedAt.plusSeconds(60) else null,
            ),
        )
    }

    @Suppress("LongParameterList")
    private fun workoutSet(
        id: String,
        order: Int,
        tracking: ExerciseTrackingType,
        load: Double?,
        isWarmup: Boolean,
        at: Instant,
    ): WorkoutSet =
        success(
            WorkoutSet.create(
                id = WorkoutSetId(id),
                order = order,
                trackingType = tracking,
                load = load,
                reps = if (tracking == ExerciseTrackingType.DURATION) null else 5,
                durationSeconds = if (tracking == ExerciseTrackingType.DURATION) 60 else null,
                rpe = null,
                isWarmup = isWarmup,
                createdAt = at,
                updatedAt = at,
            ),
        )

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }
}
