package com.repflow.app.application.workout

import com.repflow.app.application.progress.ProgressFixtures
import com.repflow.app.domain.exercise.ExerciseTrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Remediation-1-remediation-1 CP9 (B3, Q8): the last working set of the most recent valid session. */
class LastPerformanceTest {
    private val f = ProgressFixtures()
    private val may5: Instant = Instant.parse("2026-05-05T08:00:00Z")
    private val may12: Instant = Instant.parse("2026-05-12T08:00:00Z")
    private val may19: Instant = Instant.parse("2026-05-19T08:00:00Z")

    @Test
    fun `the most recent session wins whatever the list order`() {
        val last =
            lastPerformancesOf(
                listOf(
                    f.session(may12, f.entry(f.bench, f.loaded(82.5, 8))),
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 8))),
                ),
            ).getValue(f.bench)

        assertEquals(82.5, last.load)
        assertEquals(8, last.reps)
        assertEquals(may12, last.date)
    }

    @Test
    fun `within a session it is the last working set, never a warm-up`() {
        val last =
            lastPerformancesOf(
                listOf(
                    f.session(
                        may5,
                        f.entry(
                            f.bench,
                            f.loaded(40.0, 10, warmup = true),
                            f.loaded(80.0, 8),
                            f.loaded(82.5, 6),
                            f.loaded(50.0, 12, warmup = true),
                        ),
                    ),
                ),
            ).getValue(f.bench)

        assertEquals(82.5, last.load)
        assertEquals(6, last.reps)
    }

    @Test
    fun `a warm-up-only or empty session is passed over for the one before it`() {
        val last =
            lastPerformancesOf(
                listOf(
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 8))),
                    f.session(may12, f.entry(f.bench, f.loaded(40.0, 10, warmup = true))),
                    f.session(may19, f.entry(f.bench)),
                ),
            ).getValue(f.bench)

        assertEquals(80.0, last.load)
        assertEquals(may5, last.date)
    }

    @Test
    fun `an invalidated session is passed over`() {
        val last =
            lastPerformancesOf(
                listOf(
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 8))),
                    f.session(may12, f.entry(f.bench, f.loaded(200.0, 1)), invalidated = true),
                ),
            ).getValue(f.bench)

        assertEquals(80.0, last.load)
    }

    @Test
    fun `a never-done exercise has no entry`() {
        val lasts = lastPerformancesOf(listOf(f.session(may5, f.entry(f.bench, f.loaded(80.0, 8)))))

        assertNull(lasts[f.squat])
        assertTrue(lastPerformancesOf(emptyList()).isEmpty())
    }

    @Test
    fun `each exercise reads its own history`() {
        val lasts =
            lastPerformancesOf(
                listOf(
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 8)), f.entry(f.squat, f.loaded(100.0, 5))),
                    f.session(may12, f.entry(f.bench, f.loaded(82.5, 8))),
                ),
            )

        assertEquals(82.5, lasts.getValue(f.bench).load)
        assertEquals(100.0, lasts.getValue(f.squat).load)
        assertEquals(may5, lasts.getValue(f.squat).date)
    }

    @Test
    fun `reps-only and timed exercises carry their own fields`() {
        val pullUp =
            com.repflow.app.domain.exercise
                .ExerciseId("pull-up")
        val plank =
            com.repflow.app.domain.exercise
                .ExerciseId("plank")
        val lasts =
            lastPerformancesOf(
                listOf(
                    f.session(
                        may5,
                        ProgressFixtures.Entry(
                            pullUp,
                            listOf(f.set(type = ExerciseTrackingType.REPS_ONLY, reps = 9)),
                            ExerciseTrackingType.REPS_ONLY,
                        ),
                        ProgressFixtures.Entry(
                            plank,
                            listOf(f.set(type = ExerciseTrackingType.DURATION, seconds = 45)),
                            ExerciseTrackingType.DURATION,
                        ),
                    ),
                ),
            )

        assertEquals(9, lasts.getValue(pullUp).reps)
        assertNull(lasts.getValue(pullUp).load)
        assertEquals(45, lasts.getValue(plank).durationSeconds)
        assertNull(lasts.getValue(plank).reps)
    }
}
