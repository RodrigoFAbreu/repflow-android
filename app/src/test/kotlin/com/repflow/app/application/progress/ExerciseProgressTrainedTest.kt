package com.repflow.app.application.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

/** Remediation-1-remediation-1 CP2 (B6): an exercise with 0 sets in a session was not trained there. */
class ExerciseProgressTrainedTest {
    private val f = ProgressFixtures()
    private val may5: Instant = Instant.parse("2026-05-05T08:00:00Z")
    private val may12: Instant = Instant.parse("2026-05-12T08:00:00Z")

    @Test
    fun `an exercise with no sets in the latest session is not the most recent`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 5)), f.entry(f.squat, f.loaded(100.0, 5))),
                    // Squat was added to the later workout but never logged.
                    f.session(may12, f.entry(f.bench, f.loaded(82.5, 5)), f.entry(f.squat)),
                ),
            )

        assertEquals(listOf(f.bench, f.squat), progress.map { it.exerciseId })
        assertEquals(may5, progress.single { it.exerciseId == f.squat }.lastTrainedAt)
    }

    @Test
    fun `a session with only the untrained exercise contributes no point and no session`() {
        val squat =
            exerciseProgressOf(
                listOf(
                    f.session(may5, f.entry(f.squat, f.loaded(100.0, 5))),
                    f.session(may12, f.entry(f.squat)),
                ),
            ).single()

        assertEquals(
            1,
            squat.series
                .getValue(ProgressMetric.TOP_SET)
                .points.size,
        )
        assertEquals(1, squat.sessionCount(ProgressRange.ALL, may12, ZoneOffset.UTC))
        assertEquals(1, squat.performances.size)
    }

    @Test
    fun `an exercise with no sets in every session is absent`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    f.session(may5, f.entry(f.bench, f.loaded(80.0, 5)), f.entry(f.squat)),
                    f.session(may12, f.entry(f.squat)),
                ),
            )

        assertEquals(listOf(f.bench), progress.map { it.exerciseId })
    }

    @Test
    fun `a warm-up-only occurrence is still trained for ordering but gives no session or point`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    f.session(may5, f.entry(f.squat, f.loaded(100.0, 5)), f.entry(f.bench, f.loaded(80.0, 5))),
                    f.session(may12, f.entry(f.squat, f.loaded(60.0, 5, warmup = true))),
                ),
            )

        assertEquals(listOf(f.squat, f.bench), progress.map { it.exerciseId })
        val squat = progress.first()
        assertEquals(may12, squat.lastTrainedAt)
        assertEquals(
            1,
            squat.series
                .getValue(ProgressMetric.TOP_SET)
                .points.size,
        )
        assertEquals(1, squat.sessionCount(ProgressRange.ALL, may12, ZoneOffset.UTC))
    }

    @Test
    fun `name and tracking type come from the last counted occurrence`() {
        val progress =
            exerciseProgressOf(
                listOf(
                    f.session(may5, ProgressFixtures.Entry(f.bench, listOf(f.loaded(80.0, 5)), name = "Bench press")),
                    // A later, empty occurrence under a new name is not counted, so it does not rename the exercise.
                    f.session(may12, ProgressFixtures.Entry(f.bench, emptyList(), name = "Bench (renamed)")),
                ),
            ).single()

        assertEquals("Bench press", progress.name)
        assertEquals(may5, progress.lastTrainedAt)
        assertTrue(
            progress.series
                .getValue(ProgressMetric.TOP_SET)
                .points
                .single()
                .value
                .compareTo(BigDecimal(80)) == 0,
        )
    }
}
