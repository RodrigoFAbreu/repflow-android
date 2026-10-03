package com.repflow.app.application.progress

import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Remediation-1-remediation-1 CP2 (Q6): the tiles, weekly frequency and records for one exercise. */
class ExerciseProgressDetailTest {
    private val f = ProgressFixtures()
    private val utc: ZoneId = ZoneOffset.UTC
    private val now: Instant = Instant.parse("2026-05-15T10:00:00Z")

    private fun at(text: String): Instant = Instant.parse(text)

    private fun bench(vararg sessions: com.repflow.app.domain.workout.WorkoutSession): ExerciseProgress =
        exerciseProgressOf(sessions.toList()).single { it.exerciseId == f.bench }

    @Test
    fun `sessions counts valid sessions with a working set inside the range`() {
        val progress =
            bench(
                f.session(at("2025-12-01T08:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-03-10T08:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-04-10T08:00:00Z"), f.entry(f.bench, f.loaded(40.0, 10, warmup = true))),
                f.session(at("2026-05-10T08:00:00Z"), f.entry(f.bench, f.loaded(82.5, 5)), f.entry(f.bench, f.loaded(85.0, 3))),
                f.session(at("2026-05-12T08:00:00Z"), f.entry(f.bench, f.loaded(200.0, 5)), invalidated = true),
            )

        assertEquals(3, progress.sessionCount(ProgressRange.ALL, now, utc))
        assertEquals(2, progress.sessionCount(ProgressRange.THREE_MONTHS, now, utc))
        assertEquals(3, progress.sessionCount(ProgressRange.SIX_MONTHS, now, utc))
    }

    @Test
    fun `average rpe is the mean of recorded values over working sets in range, and null when none is recorded`() {
        val progress =
            bench(
                f.session(at("2025-12-01T08:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5, rpe = 10.0))),
                f.session(
                    at("2026-04-01T08:00:00Z"),
                    f.entry(f.bench, f.loaded(80.0, 5, rpe = 7.0), f.loaded(80.0, 5), f.loaded(60.0, 8, rpe = 9.0, warmup = true)),
                ),
                f.session(at("2026-05-01T08:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5, rpe = 8.5))),
            )

        assertEquals(0, BigDecimal("7.8").compareTo(progress.averageRpe(ProgressRange.THREE_MONTHS, now, utc)))
        assertEquals(0, BigDecimal("8.5").compareTo(progress.averageRpe(ProgressRange.ALL, now, utc)))
        val none = bench(f.session(at("2026-05-01T08:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))))
        assertNull(none.averageRpe(ProgressRange.ALL, now, utc))
    }

    @Test
    fun `frequency is eight monday-start weeks ending this week, averaged over all eight`() {
        // 15 May 2026 is a Friday; this week starts Monday 11 May, the first of the eight on 23 March.
        val progress =
            bench(
                // Sunday 10 May is the previous week; Monday 11 May is this one.
                f.session(at("2026-05-10T20:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-05-11T06:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-05-13T06:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-03-23T06:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                // Before the first of the eight weeks: not counted.
                f.session(at("2026-03-22T06:00:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
            )

        val frequency = progress.weeklyFrequency(now, utc)

        assertEquals(FREQUENCY_WEEKS, frequency.weeks.size)
        assertEquals(LocalDate.of(2026, 3, 23), frequency.weeks.first().weekStart)
        assertEquals(LocalDate.of(2026, 5, 11), frequency.weeks.last().weekStart)
        assertEquals(listOf(1, 0, 0, 0, 0, 0, 1, 2), frequency.weeks.map { it.sessions })
        assertEquals(0, BigDecimal("0.5").compareTo(frequency.averagePerWeek))
    }

    @Test
    fun `a daylight saving change does not move a session across a week boundary`() {
        val london = ZoneId.of("Europe/London")
        // Clocks go forward on Sunday 29 March 2026. 23:30Z that Sunday is 00:30 BST on Monday 30 March.
        val progress =
            bench(
                f.session(at("2026-03-29T23:30:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
                f.session(at("2026-03-29T22:30:00Z"), f.entry(f.bench, f.loaded(80.0, 5))),
            )

        val weeks = progress.weeklyFrequency(at("2026-04-02T10:00:00Z"), london).weeks

        assertEquals(LocalDate.of(2026, 3, 30), weeks.last().weekStart)
        assertEquals(1, weeks.last().sessions)
        assertEquals(LocalDate.of(2026, 3, 23), weeks[weeks.size - 2].weekStart)
        assertEquals(1, weeks[weeks.size - 2].sessions)
    }

    @Test
    fun `records pick the heaviest working load, then most reps at it, then the latest date`() {
        val progress =
            bench(
                f.session(at("2026-04-01T08:00:00Z"), f.entry(f.bench, f.loaded(100.0, 1, warmup = true), f.loaded(80.0, 6))),
                f.session(at("2026-04-08T08:00:00Z"), f.entry(f.bench, f.loaded(82.5, 5), f.loaded(82.5, 7))),
                f.session(at("2026-04-15T08:00:00Z"), f.entry(f.bench, f.loaded(82.5, 7))),
            )

        val records = progress.records() as ExerciseRecords.Loaded

        assertEquals(0, BigDecimal("82.5").compareTo(records.heaviestSet.load))
        assertEquals(7, records.heaviestSet.reps)
        assertEquals(at("2026-04-15T08:00:00Z"), records.heaviestSet.on)
    }

    @Test
    fun `records ignore the range and the best estimate is the chart value, ties to the latest date`() {
        // 100 x 5 = 100 * 36 / 32 = 112.5 -> 113; 90 x 8 = 90 * 36 / 29 = 111.7 -> 112; 113 again later by a single at 113.
        val progress =
            bench(
                f.session(at("2025-01-01T08:00:00Z"), f.entry(f.bench, f.loaded(100.0, 5))),
                f.session(at("2026-04-01T08:00:00Z"), f.entry(f.bench, f.loaded(90.0, 8))),
                f.session(at("2026-04-20T08:00:00Z"), f.entry(f.bench, f.loaded(113.0, 1))),
            )

        val records = progress.records() as ExerciseRecords.Loaded
        val estimate = requireNotNull(records.bestEstimatedOneRepMax)

        assertEquals(0, BigDecimal(113).compareTo(estimate.value))
        assertEquals(at("2026-04-20T08:00:00Z"), estimate.on)
        assertEquals(0, BigDecimal(113).compareTo(records.heaviestSet.load))
        assertEquals(
            progress.series
                .getValue(ProgressMetric.ESTIMATED_ONE_REP_MAX)
                .best
                ?.stripTrailingZeros(),
            estimate.value.stripTrailingZeros(),
        )
    }

    @Test
    fun `with no set of twelve reps or fewer the estimate is absent but the heaviest set stays`() {
        val progress = bench(f.session(at("2026-04-01T08:00:00Z"), f.entry(f.bench, f.loaded(15.0, 15))))

        val records = progress.records() as ExerciseRecords.Loaded

        assertNull(records.bestEstimatedOneRepMax)
        assertEquals(15, records.heaviestSet.reps)
    }

    @Test
    fun `an exercise whose working sets carry no load has no loaded records`() {
        val progress = bench(f.session(at("2026-04-01T08:00:00Z"), f.entry(f.bench, f.set(reps = 12))))

        assertNull(progress.records())
    }

    @Test
    fun `reps-only and timed exercises have one record each`() {
        val pullUps =
            exerciseProgressOf(
                listOf(
                    f.session(
                        at("2026-04-01T08:00:00Z"),
                        ProgressFixtures.Entry(
                            f.squat,
                            listOf(f.set(ExerciseTrackingType.REPS_ONLY, reps = 12)),
                            ExerciseTrackingType.REPS_ONLY,
                        ),
                    ),
                    f.session(
                        at("2026-04-08T08:00:00Z"),
                        ProgressFixtures.Entry(
                            f.squat,
                            listOf(f.set(ExerciseTrackingType.REPS_ONLY, reps = 12)),
                            ExerciseTrackingType.REPS_ONLY,
                        ),
                    ),
                ),
            ).single()
        val reps = pullUps.records() as ExerciseRecords.MostReps
        assertEquals(12, reps.reps)
        assertEquals(at("2026-04-08T08:00:00Z"), reps.on)

        val plank =
            exerciseProgressOf(
                listOf(
                    f.session(
                        at("2026-04-01T08:00:00Z"),
                        ProgressFixtures.Entry(
                            f.squat,
                            listOf(f.set(ExerciseTrackingType.DURATION, seconds = 60), f.set(ExerciseTrackingType.DURATION, seconds = 45)),
                            ExerciseTrackingType.DURATION,
                        ),
                    ),
                ),
            ).single()
        val hold = plank.records() as ExerciseRecords.LongestHold
        assertEquals(60, hold.seconds)
        assertTrue(plank.records() !is ExerciseRecords.Loaded)
    }
}
