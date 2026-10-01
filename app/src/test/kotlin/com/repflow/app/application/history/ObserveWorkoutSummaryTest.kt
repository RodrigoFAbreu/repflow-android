package com.repflow.app.application.history

import app.cash.turbine.test
import com.repflow.app.application.workout.InMemoryWorkoutRepository
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The done screen's read model (remediation-1 CP9): the recap against the
 * last time each exercise was trained (`D67`) and the best sets (`D68`), both
 * derived from valid completed sessions only.
 */
class ObserveWorkoutSummaryTest {
    private val squat = ExerciseId("squat")
    private val pullUp = ExerciseId("pull-up")
    private val plank = ExerciseId("plank")

    @Test
    fun `a session that is not a completed one has no summary`() {
        val active = session("active", day = 2, status = WorkoutSessionStatus.ACTIVE)
        assertNull(workoutSummaryOf(WorkoutSessionId("missing"), listOf(session("other", day = 1))))
        assertNull(workoutSummaryOf(active.id, listOf(active)))
    }

    @Test
    fun `the best set is the heaviest working load and the most reps at it, warm-ups ignored`() {
        val best =
            bestOf(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                listOf(set(100.0, 5), set(102.5, 3), set(102.5, 4), set(140.0, 1, warmup = true)),
            )
        assertEquals(BestSet.Load(102.5, 4), best)
    }

    @Test
    fun `an unloaded weight-and-reps exercise, reps only and a timed hold each judge by their own figure`() {
        assertEquals(BestSet.Reps(12), bestOf(ExerciseTrackingType.WEIGHT_AND_REPS, listOf(set(null, 10), set(null, 12))))
        assertEquals(BestSet.Reps(9), bestOf(ExerciseTrackingType.REPS_ONLY, listOf(set(null, 9), set(null, 7))))
        assertEquals(BestSet.Seconds(75), bestOf(ExerciseTrackingType.DURATION, listOf(seconds(60), seconds(75))))
        assertNull(bestOf(ExerciseTrackingType.WEIGHT_AND_REPS, listOf(set(60.0, 10, warmup = true))))
    }

    @Test
    fun `last time is the most recent earlier valid session with the exercise, skipping invalidated and later ones`() {
        val oldest = session("oldest", day = 1, exercises = listOf(entry(squat, set(90.0, 5))))
        val newer = session("newer", day = 2, exercises = listOf(entry(squat, set(95.0, 5))))
        val invalidated = session("invalidated", day = 3, exercises = listOf(entry(squat, set(120.0, 5))), invalidated = true)
        val unrelated = session("unrelated", day = 4, exercises = listOf(entry(plank, seconds(60))))
        val current = session("current", day = 5, exercises = listOf(entry(squat, set(100.0, 5))))
        val later = session("later", day = 6, exercises = listOf(entry(squat, set(110.0, 5))))

        val summary = checkNotNull(workoutSummaryOf(current.id, listOf(oldest, newer, invalidated, unrelated, current, later)))

        assertEquals(BestSet.Load(100.0, 5), summary.recaps.single().best)
        assertEquals(BestSet.Load(95.0, 5), summary.recaps.single().lastTime)
    }

    @Test
    fun `a first time, a different measure, and no working sets have nothing to compare`() {
        val earlier = session("earlier", day = 1, exercises = listOf(entry(pullUp, set(null, 8))))
        val current =
            session(
                "current",
                day = 2,
                exercises =
                    listOf(
                        // Logged with a load this time: not comparable to last time's reps-only best.
                        entry(pullUp, set(10.0, 6)),
                        entry(squat, set(100.0, 5)),
                        entry(plank, seconds(30, warmup = true)),
                    ),
            )

        val recaps = checkNotNull(workoutSummaryOf(current.id, listOf(earlier, current))).recaps

        assertNull(recaps[0].lastTime)
        assertNull(recaps[1].lastTime)
        assertNull(recaps[2].best)
        assertNull(recaps[2].lastTime)
    }

    @Test
    fun `a best set beats every earlier valid working set, and names the previous best and its day`() {
        val first = session("first", day = 1, exercises = listOf(entry(squat, set(80.0, 7))))
        val second = session("second", day = 2, exercises = listOf(entry(squat, set(80.0, 7))))
        val invalidated = session("invalidated", day = 3, exercises = listOf(entry(squat, set(150.0, 1))), invalidated = true)
        val current = session("current", day = 4, exercises = listOf(entry(squat, set(82.5, 7))))

        val best = checkNotNull(workoutSummaryOf(current.id, listOf(first, second, invalidated, current))).personalBests.single()

        assertEquals(BestSet.Load(82.5, 7), best.best)
        assertEquals(BestSet.Load(80.0, 7), best.previous)
        // A tie keeps the most recent session that set it.
        assertEquals(second.endedAt, best.previousEndedAt)
    }

    @Test
    fun `matching or falling short of the record, and a first time, are not best sets`() {
        val earlier = session("earlier", day = 1, exercises = listOf(entry(squat, set(100.0, 5)), entry(plank, seconds(60))))
        val current =
            session(
                "current",
                day = 2,
                exercises = listOf(entry(squat, set(100.0, 5)), entry(plank, seconds(45)), entry(pullUp, set(null, 10))),
            )

        assertTrue(checkNotNull(workoutSummaryOf(current.id, listOf(earlier, current))).personalBests.isEmpty())
    }

    @Test
    fun `more reps at the same top load is a best set`() {
        val earlier = session("earlier", day = 1, exercises = listOf(entry(squat, set(100.0, 5))))
        val current = session("current", day = 2, exercises = listOf(entry(squat, set(100.0, 6))))

        val best = checkNotNull(workoutSummaryOf(current.id, listOf(earlier, current))).personalBests.single()

        assertEquals(BestSet.Load(100.0, 6), best.best)
    }

    @Test
    fun `the flow emits the summary once the session is completed`() =
        runTest {
            val repository = InMemoryWorkoutRepository()
            val completed = session("done", day = 1, exercises = listOf(entry(squat, set(100.0, 5))))
            ObserveWorkoutSummary(repository)(completed.id).test {
                assertNull(awaitItem())

                repository.insert(completed)

                assertEquals(completed.id, awaitItem()?.session?.id)
            }
        }

    private data class Entry(
        val exerciseId: ExerciseId,
        val sets: List<SetSpec>,
    )

    private data class SetSpec(
        val load: Double?,
        val reps: Int?,
        val seconds: Int?,
        val warmup: Boolean,
    )

    private fun entry(
        exerciseId: ExerciseId,
        vararg sets: SetSpec,
    ) = Entry(exerciseId, sets.toList())

    private fun set(
        load: Double?,
        reps: Int,
        warmup: Boolean = false,
    ) = SetSpec(load, reps, null, warmup)

    private fun seconds(
        seconds: Int,
        warmup: Boolean = false,
    ) = SetSpec(null, null, seconds, warmup)

    private fun bestOf(
        trackingType: ExerciseTrackingType,
        specs: List<SetSpec>,
    ): BestSet? = bestSetOf(trackingType, specs.mapIndexed { index, spec -> workoutSet("s-$index", index, trackingType, spec, AT) })

    private fun session(
        id: String,
        day: Int,
        exercises: List<Entry> = emptyList(),
        invalidated: Boolean = false,
        status: WorkoutSessionStatus = WorkoutSessionStatus.COMPLETED,
    ): WorkoutSession {
        val startedAt = Instant.parse("2026-08-0${day}T10:00:00Z")
        val endedAt = startedAt.plusSeconds(3_600)
        val sessionId = WorkoutSessionId(id)
        val workoutExercises =
            exercises.mapIndexed { index, entry ->
                val tracking = trackingOf(entry)
                success(
                    WorkoutExercise.create(
                        id = WorkoutExerciseId("$id-$index"),
                        sessionId = sessionId,
                        exerciseId = entry.exerciseId,
                        order = index,
                        exerciseNameSnapshot = entry.exerciseId.value,
                        trackingType = tracking,
                        plannedExerciseId = null,
                        sets =
                            entry.sets.mapIndexed {
                                setIndex,
                                spec,
                                ->
                                workoutSet("$id-$index-$setIndex", setIndex, tracking, spec, startedAt)
                            },
                    ),
                )
            }
        val terminal = status != WorkoutSessionStatus.ACTIVE
        return success(
            WorkoutSession.reconstruct(
                id = sessionId,
                trainingPlanVersionId = null,
                status = status,
                startedAt = startedAt,
                endedAt = if (terminal) endedAt else null,
                exercises = workoutExercises,
                invalidatedAt = if (invalidated) endedAt.plusSeconds(60) else null,
            ),
        )
    }

    private fun trackingOf(entry: Entry): ExerciseTrackingType =
        when {
            entry.sets.any { it.seconds != null } -> ExerciseTrackingType.DURATION
            entry.exerciseId == pullUp && entry.sets.all { it.load == null } -> ExerciseTrackingType.REPS_ONLY
            else -> ExerciseTrackingType.WEIGHT_AND_REPS
        }

    private fun workoutSet(
        id: String,
        order: Int,
        tracking: ExerciseTrackingType,
        spec: SetSpec,
        at: Instant,
    ): WorkoutSet =
        success(
            WorkoutSet.create(
                id = WorkoutSetId(id),
                order = order,
                trackingType = tracking,
                load = spec.load,
                reps = spec.reps,
                durationSeconds = spec.seconds,
                rpe = null,
                isWarmup = spec.warmup,
                createdAt = at,
                updatedAt = at,
            ),
        )

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private companion object {
        val AT: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
