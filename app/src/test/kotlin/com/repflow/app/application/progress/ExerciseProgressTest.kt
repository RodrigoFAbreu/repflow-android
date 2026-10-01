package com.repflow.app.application.progress

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Remediation-1 CP15's read model (plan items 3, 4, 6, 7 and 8): the offered
 * metric set per tracking type, each metric's value, the unsupported-versus-
 * empty distinction, unloaded sessions contributing no point, and invalidated
 * sessions removed.
 */
class ExerciseProgressTest {
    private val bench = ExerciseId("bench")
    private val pullUps = ExerciseId("pull-ups")
    private val plank = ExerciseId("plank")
    private val day0: Instant = Instant.parse("2026-05-05T08:00:00Z")
    private var nextId = 0

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private fun set(
        type: ExerciseTrackingType,
        load: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
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
                rpe = null,
                isWarmup = warmup,
                createdAt = day0,
                updatedAt = day0,
            ),
        )
    }

    private data class Entry(
        val exerciseId: ExerciseId,
        val type: ExerciseTrackingType,
        val sets: List<WorkoutSet>,
        val name: String = exerciseIdName(exerciseId),
    )

    private fun session(
        day: Long,
        vararg entries: Entry,
        invalidated: Boolean = false,
    ): WorkoutSession {
        val id = WorkoutSessionId("session-$day-${nextId++}")
        val startedAt = day0.plus(Duration.ofDays(day))
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

    private fun benchEntry(vararg sets: WorkoutSet) = Entry(bench, ExerciseTrackingType.WEIGHT_AND_REPS, sets.toList())

    private fun loaded(
        load: Double,
        reps: Int,
        warmup: Boolean = false,
    ) = set(ExerciseTrackingType.WEIGHT_AND_REPS, load = load, reps = reps, warmup = warmup)

    private fun values(series: ProgressSeries?): List<BigDecimal> = series!!.points.map { it.value.stripTrailingZeros() }

    private fun decimals(vararg values: String) = values.map { BigDecimal(it).stripTrailingZeros() }

    private fun List<ExerciseProgress>.of(id: ExerciseId) = single { it.exerciseId == id }

    @Test
    fun `weight-and-reps offers all three metrics, from working sets only`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    // The 100 kg warm-up is heavier than any working set and must not count.
                    session(0, benchEntry(loaded(100.0, 1, warmup = true), loaded(80.0, 8), loaded(85.0, 3))),
                    session(7, benchEntry(loaded(90.0, 5))),
                ),
            ).of(bench)

        assertEquals(ProgressMetric.entries.toList(), progress.offeredMetrics)
        assertEquals(progress.offeredMetrics.toSet(), progress.series.keys)
        // Top set: the heaviest working load.
        assertEquals(decimals("85", "90"), values(progress.series[ProgressMetric.TOP_SET]))
        // Est. 1RM: best per-set Brzycki, whole kg - 80 × 36 / 29 = 99.3 beats 85 × 36 / 34 = 90; then 90 × 36 / 32 = 101.25.
        assertEquals(decimals("99", "101"), values(progress.series[ProgressMetric.ESTIMATED_ONE_REP_MAX]))
        // Volume: Σ load × reps over working sets - 640 + 255, then 450.
        assertEquals(decimals("895", "450"), values(progress.series[ProgressMetric.VOLUME]))

        val topSet = progress.series.getValue(ProgressMetric.TOP_SET)
        assertTrue(topSet.hasTrend)
        assertEquals(0, BigDecimal("5").compareTo(topSet.delta))
        assertEquals(0, BigDecimal("90").compareTo(topSet.best))
        assertEquals(day0, topSet.windowStart?.startedAt)
        assertEquals(0, BigDecimal("-445").compareTo(progress.series.getValue(ProgressMetric.VOLUME).delta))
    }

    @Test
    fun `a reps-only exercise with many sessions offers top set alone, as the most reps - unsupported, not empty`() {
        val sessions =
            (0L until 5L).map { day ->
                session(
                    day,
                    Entry(
                        pullUps,
                        ExerciseTrackingType.REPS_ONLY,
                        listOf(
                            set(ExerciseTrackingType.REPS_ONLY, reps = 20, warmup = true),
                            set(
                                ExerciseTrackingType.REPS_ONLY,
                                reps =
                                    6 + day.toInt(),
                            ),
                        ),
                    ),
                )
            }

        val progress = exerciseProgressOf(sessions).of(pullUps)

        assertEquals(listOf(ProgressMetric.TOP_SET), progress.offeredMetrics)
        assertEquals(setOf(ProgressMetric.TOP_SET), progress.series.keys)
        assertEquals(decimals("6", "7", "8", "9", "10"), values(progress.series[ProgressMetric.TOP_SET]))
        assertTrue(progress.series.getValue(ProgressMetric.TOP_SET).hasTrend)
    }

    @Test
    fun `a duration exercise with many sessions offers top set alone, as the longest set in seconds`() {
        val sessions =
            (0L until 4L).map { day ->
                session(
                    day,
                    Entry(
                        plank,
                        ExerciseTrackingType.DURATION,
                        listOf(
                            set(ExerciseTrackingType.DURATION, seconds = 30),
                            set(
                                ExerciseTrackingType.DURATION,
                                seconds =
                                    45 + day.toInt() * 5,
                            ),
                        ),
                    ),
                )
            }

        val progress = exerciseProgressOf(sessions).of(plank)

        assertEquals(listOf(ProgressMetric.TOP_SET), progress.offeredMetrics)
        assertEquals(setOf(ProgressMetric.TOP_SET), progress.series.keys)
        assertEquals(decimals("45", "50", "55", "60"), values(progress.series[ProgressMetric.TOP_SET]))
        assertTrue(progress.series.getValue(ProgressMetric.TOP_SET).hasTrend)
    }

    @Test
    fun `a weight-and-reps exercise with one session offers every metric, each in its empty state`() {
        val progress = exerciseProgressOf(listOf(session(0, benchEntry(loaded(80.0, 5))))).of(bench)

        assertEquals(ProgressMetric.entries.toList(), progress.offeredMetrics)
        progress.offeredMetrics.forEach { metric ->
            val series = progress.series.getValue(metric)
            assertEquals(1, series.points.size)
            assertFalse("$metric should be in its empty state", series.hasTrend)
            assertNull(series.delta)
        }
    }

    @Test
    fun `a session whose working sets carry no load contributes no point to any metric`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    session(0, benchEntry(loaded(80.0, 5))),
                    // Loaded only on the warm-up: no working set has a load.
                    session(3, benchEntry(loaded(40.0, 10, warmup = true), set(ExerciseTrackingType.WEIGHT_AND_REPS, reps = 12))),
                    session(7, benchEntry(loaded(82.5, 5))),
                ),
            ).of(bench)

        ProgressMetric.entries.forEach { metric ->
            assertEquals(
                "$metric",
                2,
                progress.series
                    .getValue(metric)
                    .points.size,
            )
            assertTrue(
                progress.series
                    .getValue(metric)
                    .points
                    .none { it.startedAt == day0.plus(Duration.ofDays(3)) },
            )
        }
    }

    @Test
    fun `invalidated and unfinished sessions never count`() {
        val abandoned =
            success(
                WorkoutSession.reconstruct(
                    id = WorkoutSessionId("abandoned"),
                    trainingPlanVersionId = null,
                    status = WorkoutSessionStatus.ABANDONED,
                    startedAt = day0,
                    endedAt = day0.plusSeconds(60),
                    exercises = emptyList(),
                ),
            )
        val progress =
            exerciseProgressOf(
                listOf(
                    session(0, benchEntry(loaded(80.0, 5))),
                    session(3, benchEntry(loaded(200.0, 5)), invalidated = true),
                    session(7, benchEntry(loaded(82.5, 5))),
                    abandoned,
                ),
            ).of(bench)

        assertEquals(decimals("80", "82.5"), values(progress.series[ProgressMetric.TOP_SET]))
    }

    @Test
    fun `invalidating a session removes its point from the live read model`() =
        runTest {
            val repository = InMemoryWorkoutRepository()
            val first = session(0, benchEntry(loaded(80.0, 5)))
            val second = session(7, benchEntry(loaded(85.0, 5)))
            repository.insert(first)
            repository.insert(second)

            ObserveExerciseProgress(repository)().test {
                assertEquals(decimals("80", "85"), values(awaitItem().of(bench).series[ProgressMetric.TOP_SET]))

                repository.update(success(second.invalidate(second.endedAt!!.plusSeconds(60))))

                val after = awaitItem().of(bench).series.getValue(ProgressMetric.TOP_SET)
                assertEquals(decimals("80"), values(after))
                assertFalse(after.hasTrend)
            }
        }

    @Test
    fun `the window is the last twelve sessions, and the delta and best are measured within it`() {
        val sessions = (0L until 14L).map { day -> session(day, benchEntry(loaded(60.0 + day * 2.5, 5))) }

        val topSet = exerciseProgressOf(sessions).of(bench).series.getValue(ProgressMetric.TOP_SET)

        assertEquals(14, topSet.points.size)
        assertEquals(ProgressSeries.WINDOW_SIZE, topSet.window.size)
        assertEquals(day0.plus(Duration.ofDays(2)), topSet.windowStart?.startedAt)
        assertEquals(0, BigDecimal("27.5").compareTo(topSet.delta))
        assertEquals(0, BigDecimal("92.5").compareTo(topSet.best))
    }

    @Test
    fun `exercises are ordered most recently trained first, named and typed by their latest session`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    session(
                        0,
                        Entry(
                            bench,
                            ExerciseTrackingType.REPS_ONLY,
                            listOf(set(ExerciseTrackingType.REPS_ONLY, reps = 15)),
                            name = "Bench (old)",
                        ),
                    ),
                    session(3, Entry(plank, ExerciseTrackingType.DURATION, listOf(set(ExerciseTrackingType.DURATION, seconds = 40)))),
                    session(7, benchEntry(loaded(80.0, 5))),
                ),
            )

        assertEquals(listOf(bench, plank), progress.map { it.exerciseId })
        val benchProgress = progress.of(bench)
        assertEquals("Bench", benchProgress.name)
        assertEquals(ExerciseTrackingType.WEIGHT_AND_REPS, benchProgress.trackingType)
        // The earlier reps-only session is recorded under another type and gives no point.
        assertEquals(decimals("80"), values(benchProgress.series[ProgressMetric.TOP_SET]))
    }

    @Test
    fun `an exercise logged twice in one session is one point over all its sets`() {
        val progress =
            exerciseProgressOf(listOf(session(0, benchEntry(loaded(80.0, 5)), benchEntry(loaded(90.0, 2))))).of(bench)

        assertEquals(decimals("90"), values(progress.series[ProgressMetric.TOP_SET]))
        assertEquals(decimals("580"), values(progress.series[ProgressMetric.VOLUME]))
    }

    private companion object {
        fun exerciseIdName(id: ExerciseId): String =
            when (id.value) {
                "bench" -> "Bench"
                "pull-ups" -> "Pull-ups"
                else -> "Plank"
            }
    }
}
