package com.repflow.app.presentation.history

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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

/**
 * History's plain values (remediation-1 CP12): the set count and volume a row
 * and the detail's tiles show, the month sections, and what one logged set
 * reads as - including the parent milestone's DURATION defect, where a timed
 * set rendered as `Set 0:  kg x `.
 */
class HistoryModelTest {
    @Test
    fun `sets count working sets only and volume sums load times reps over them`() {
        val session =
            session(
                "s",
                "2026-08-08T10:00:00Z",
                exercise(
                    ExerciseTrackingType.WEIGHT_AND_REPS,
                    set(load = 40.0, reps = 10, warmup = true),
                    set(load = 82.5, reps = 7),
                    set(load = 80.0, reps = 6),
                ),
                exercise(ExerciseTrackingType.REPS_ONLY, set(tracking = ExerciseTrackingType.REPS_ONLY, reps = 12)),
            )

        assertEquals(3, workingSetCount(session))
        // 82.5 × 7 + 80 × 6 = 577.5 + 480 = 1057.5, rounded half up.
        assertEquals(BigDecimal("1058"), volumeKg(session))
    }

    @Test
    fun `a session with no loaded working set has no volume`() {
        val session =
            session(
                "s",
                "2026-08-08T10:00:00Z",
                exercise(ExerciseTrackingType.DURATION, set(tracking = ExerciseTrackingType.DURATION, seconds = 45)),
                exercise(ExerciseTrackingType.WEIGHT_AND_REPS, set(load = 60.0, reps = 5, warmup = true)),
            )

        assertNull(volumeKg(session))
    }

    @Test
    fun `month sections follow the list's order and never repeat a month`() {
        val aug2 = session("a", "2026-08-02T10:00:00Z")
        val aug1 = session("b", "2026-08-01T10:00:00Z")
        val jul = session("c", "2026-07-31T10:00:00Z")

        val newestFirst = monthSections(listOf(aug2, aug1, jul), ZoneOffset.UTC)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7)), newestFirst.map { it.month })
        assertEquals(listOf(aug2, aug1), newestFirst.first().sessions)

        val oldestFirst = monthSections(listOf(jul, aug1, aug2), ZoneOffset.UTC)
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8)), oldestFirst.map { it.month })
    }

    @Test
    fun `a timed set reads as its seconds and never as a load and reps`() {
        assertEquals(
            HistorySetValue.Seconds(45),
            historySetValueOf(ExerciseTrackingType.DURATION, set(tracking = ExerciseTrackingType.DURATION, seconds = 45)),
        )
    }

    @Test
    fun `a weighted set reads as load and reps, and an unloaded one as reps`() {
        assertEquals(
            HistorySetValue.Load(BigDecimal.valueOf(70.0), 10),
            historySetValueOf(ExerciseTrackingType.WEIGHT_AND_REPS, set(load = 70.0, reps = 10)),
        )
        assertEquals(
            HistorySetValue.Reps(12),
            historySetValueOf(ExerciseTrackingType.WEIGHT_AND_REPS, set(load = null, reps = 12)),
        )
        assertEquals(
            HistorySetValue.Reps(8),
            historySetValueOf(ExerciseTrackingType.REPS_ONLY, set(tracking = ExerciseTrackingType.REPS_ONLY, load = null, reps = 8)),
        )
    }

    @Test
    fun `working sets are numbered in order and warm-ups are not`() {
        val sets = listOf(set(load = 40.0, reps = 8, warmup = true), set(load = 80.0, reps = 5), set(load = 80.0, reps = 5))
        assertEquals(listOf(null, 1, 2), workingSetNumbers(sets))
    }

    @Test
    fun `an RPE drops a trailing zero`() {
        assertEquals("8", rpeText(8.0))
        assertEquals("8.5", rpeText(8.5))
        assertEquals("10", rpeText(10.0))
    }

    private var nextSetId = 0

    private fun set(
        tracking: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        load: Double? = null,
        reps: Int? = null,
        seconds: Int? = null,
        warmup: Boolean = false,
    ): WorkoutSet =
        success(
            WorkoutSet.create(
                id = WorkoutSetId("set-${nextSetId++}"),
                order = nextSetId,
                trackingType = tracking,
                load = load,
                reps = reps,
                durationSeconds = seconds,
                rpe = null,
                isWarmup = warmup,
                createdAt = AT,
                updatedAt = AT,
            ),
        )

    private data class ExerciseSpec(
        val tracking: ExerciseTrackingType,
        val sets: List<WorkoutSet>,
    )

    private fun exercise(
        tracking: ExerciseTrackingType,
        vararg sets: WorkoutSet,
    ) = ExerciseSpec(tracking, sets.toList())

    private fun session(
        id: String,
        startedAt: String,
        vararg exercises: ExerciseSpec,
    ): WorkoutSession {
        val sessionId = WorkoutSessionId(id)
        val started = Instant.parse(startedAt)
        return success(
            WorkoutSession.reconstruct(
                id = sessionId,
                trainingPlanVersionId = null,
                status = WorkoutSessionStatus.COMPLETED,
                startedAt = started,
                endedAt = started.plusSeconds(3_600),
                exercises =
                    exercises.mapIndexed { index, spec ->
                        success(
                            WorkoutExercise.create(
                                id = WorkoutExerciseId("$id-$index"),
                                sessionId = sessionId,
                                exerciseId = ExerciseId("ex-$index"),
                                order = index,
                                exerciseNameSnapshot = "Exercise $index",
                                trackingType = spec.tracking,
                                plannedExerciseId = null,
                                sets = spec.sets,
                            ),
                        )
                    },
                invalidatedAt = null,
            ),
        )
    }

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private companion object {
        val AT: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
