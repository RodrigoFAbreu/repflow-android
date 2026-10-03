package com.repflow.app.presentation.progress

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressPoint
import com.repflow.app.application.progress.ProgressRange
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.application.progress.SessionPerformance
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/**
 * Stateless Compose coverage for [ProgressScreen] on `5b` (remediation-1-remediation-1
 * CP3): the exercise picker and its sheet, the metric buttons, the chart card
 * with its range pills, headline, delta and readout, the scrubber, the
 * one-point and empty states, the tiles, `Training frequency` and `Records`,
 * and the dense-`All` date labels at both phone widths.
 */
@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val bench = ExerciseId("bench")
    private val pullUps = ExerciseId("pull-ups")

    /** Midday UTC, and the screen reads in UTC, so a date reads the same in any device zone. */
    private val may5: Instant = Instant.parse("2026-05-05T12:00:00Z")
    private val now: Instant = Instant.parse("2026-06-02T12:00:00Z")

    private fun string(
        id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun series(vararg values: String): ProgressSeries =
        ProgressSeries(
            values.mapIndexed { index, value ->
                ProgressPoint(WorkoutSessionId("s$index"), may5.plus(Duration.ofDays(7L * index)), BigDecimal(value))
            },
        )

    private fun workingSet(
        index: Int,
        load: Double?,
        reps: Int,
        rpe: Double?,
    ): WorkoutSet =
        (
            WorkoutSet.create(
                id = WorkoutSetId("set$index"),
                order = 0,
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                load = load,
                reps = reps,
                durationSeconds = null,
                rpe = rpe,
                isWarmup = false,
                createdAt = may5,
                updatedAt = may5,
            ) as DomainResult.Success
        ).value

    /** Four weekly sessions from 5 May: 72.5, 75 and 80 for 8, then 82.5 for 7 at RPE 9. */
    private val benchPerformances =
        listOf(72.5 to 8, 75.0 to 8, 80.0 to 8, 82.5 to 7).mapIndexed { index, (load, reps) ->
            SessionPerformance(
                sessionId = WorkoutSessionId("s$index"),
                startedAt = may5.plus(Duration.ofDays(7L * index)),
                workingSets = listOf(workingSet(index, load, reps, rpe = if (index == 3) 9.0 else 8.0)),
            )
        }

    private val benchProgress =
        ExerciseProgress(
            exerciseId = bench,
            name = "Barbell Bench Press",
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            lastTrainedAt = may5,
            series =
                mapOf(
                    ProgressMetric.TOP_SET to series("72.5", "75", "80", "82.5"),
                    ProgressMetric.ESTIMATED_ONE_REP_MAX to series("88"),
                    ProgressMetric.VOLUME to series("1740", "2310"),
                ),
            performances = benchPerformances,
        )

    private val pullUpProgress =
        ExerciseProgress(
            exerciseId = pullUps,
            name = "Pull-up",
            trackingType = ExerciseTrackingType.REPS_ONLY,
            lastTrainedAt = may5,
            series = mapOf(ProgressMetric.TOP_SET to series("6", "8", "9")),
        )

    private fun setScreen(
        uiState: ProgressUiState,
        onExerciseSelected: (ExerciseId) -> Unit = {},
        onMetricSelected: (ProgressMetric) -> Unit = {},
        onRangeSelected: (ProgressRange) -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ProgressScreen(
                    uiState = uiState,
                    onExerciseSelected = onExerciseSelected,
                    onMetricSelected = onMetricSelected,
                    onRangeSelected = onRangeSelected,
                )
            }
        }
    }

    /** The ViewModel's job, in the harness: each choice replaces the state the screen draws. */
    private fun setStatefulScreen(initial: ProgressUiState) {
        composeRule.setContent {
            RepFlowTheme {
                var state by remember { mutableStateOf(initial) }
                ProgressScreen(
                    uiState = state,
                    onExerciseSelected = { state = state.copy(selectedExerciseId = it) },
                    onMetricSelected = { state = state.copy(selectedMetric = it) },
                    onRangeSelected = { state = state.copy(range = it) },
                )
            }
        }
    }

    private fun loaded(
        selected: ExerciseId = bench,
        metric: ProgressMetric = ProgressMetric.TOP_SET,
        range: ProgressRange = ProgressRange.ALL,
    ) = ProgressUiState(
        isLoading = false,
        exercises = listOf(benchProgress, pullUpProgress),
        selectedExerciseId = selected,
        selectedMetric = metric,
        range = range,
        now = now,
        zone = ZoneOffset.UTC,
    )

    @Test
    fun theCardShowsTheLatestValueTheDeltaAndPercentTheReadoutAndTheChart() {
        setScreen(loaded())

        composeRule.onNodeWithText("82.5").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+10", "kg", "+14")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_caption_top_set).uppercase()).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "26 May")).assertIsDisplayed()
        composeRule.onNodeWithText("82.5 kg").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.resources.getQuantityString(
                    R.plurals.progress_chart_description,
                    4,
                    string(R.string.progress_metric_top_set),
                    string(R.string.progress_range_all_spoken),
                    4,
                    "72.5 kg",
                    "82.5 kg",
                ),
            ).assertIsDisplayed()
        // GF-4 (`8b`): the span under the value, and no "Only valid sessions count" note.
        composeRule
            .onNodeWithText(composeRule.activity.resources.getQuantityString(R.plurals.progress_span, 4, "5 May – 26 May 2026", 4))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Only valid sessions", substring = true).assertCountEquals(0)
    }

    @Test
    fun thePickerButtonOpensTheSheetAndASelectionReportsTheChoice() {
        var exercise: ExerciseId? = null
        var metric: ProgressMetric? = null
        setScreen(loaded(), onExerciseSelected = { exercise = it }, onMetricSelected = { metric = it })

        composeRule.onNodeWithText(string(R.string.progress_metric_top_set)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progress_picker_title).uppercase()).assertDoesNotExist()

        composeRule.onNodeWithText("Barbell Bench Press").performClick()
        composeRule.onNodeWithText(string(R.string.progress_picker_title).uppercase()).assertIsDisplayed()
        // In the sheet the chosen exercise is the selected row; the picker button repeats its name.
        composeRule.onNode(hasText("Barbell Bench Press") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Pull-up").performClick()
        composeRule.onNodeWithText(string(R.string.progress_picker_title).uppercase()).assertDoesNotExist()

        composeRule.onNodeWithText(string(R.string.progress_metric_volume)).performClick()

        assertEquals(pullUps, exercise)
        assertEquals(ProgressMetric.VOLUME, metric)
    }

    @Test
    fun aRangePillNarrowsTheChartAndChangesTheHeadline() {
        val dated =
            benchProgress.copy(
                series =
                    mapOf(
                        ProgressMetric.TOP_SET to
                            ProgressSeries(
                                listOf("2026-01-05" to "60", "2026-04-20" to "70", "2026-05-20" to "80").mapIndexed { index, (day, value) ->
                                    ProgressPoint(WorkoutSessionId("d$index"), Instant.parse("${day}T12:00:00Z"), BigDecimal(value))
                                },
                            ),
                        ProgressMetric.ESTIMATED_ONE_REP_MAX to series("88"),
                        ProgressMetric.VOLUME to series("1740", "2310"),
                    ),
            )
        setStatefulScreen(loaded().copy(exercises = listOf(dated)))

        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+20", "kg", "+33")).assertIsDisplayed()
        composeRule.onNodeWithText("All").assertIsSelected()

        composeRule.onNodeWithText("3m").performClick()

        composeRule.onNodeWithText("3m").assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+10", "kg", "+14")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+20", "kg", "+33")).assertDoesNotExist()
    }

    @Test
    fun tappingTheChartMovesTheReadoutToTheNearestPoint() {
        setScreen(loaded())

        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "26 May")).assertIsDisplayed()

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(Offset(1f, centerY)) }

        composeRule.onNodeWithText("5 May").assertIsDisplayed()
        composeRule.onNodeWithText("72.5 kg").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "26 May")).assertDoesNotExist()
    }

    /**
     * Review I-2: the chart's gesture handlers outlive a recomposition, so one
     * that captured the first touch's callback kept writing to a discarded
     * selection after the metric changed to one with the same point count.
     */
    @Test
    fun tappingTheChartAfterTouchingItAndSwitchingToAMetricWithTheSamePointCountStillMovesTheReadout() {
        val sameCount =
            benchProgress.copy(
                series =
                    mapOf(
                        ProgressMetric.TOP_SET to series("72.5", "75", "80", "82.5"),
                        ProgressMetric.ESTIMATED_ONE_REP_MAX to series("88"),
                        ProgressMetric.VOLUME to series("1740", "1800", "1900", "2310"),
                    ),
            )
        setStatefulScreen(loaded().copy(exercises = listOf(sameCount)))

        // Touch the chart first, so its gesture coroutines start with this metric's callback.
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(Offset(1f, centerY)) }
        composeRule.onNodeWithText("72.5 kg").assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.progress_metric_volume)).performClick()
        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "26 May")).assertIsDisplayed()

        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).performTouchInput { click(Offset(1f, centerY)) }

        composeRule.onNodeWithText("5 May").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "26 May")).assertDoesNotExist()
    }

    @Test
    fun volumeReadsInKilogramsTotal() {
        setScreen(loaded(metric = ProgressMetric.VOLUME))

        // Grouped as History groups its volume (functional review A5).
        composeRule.onNodeWithText(NumberFormat.getIntegerInstance(Locale.getDefault()).format(2310)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_unit_kg_total)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+570", "kg", "+33")).assertIsDisplayed()
    }

    /** Functional review J6: when every set is past the 12-rep ceiling the card says what is missing, not "no sessions". */
    @Test
    fun estimatedOneRepMaxNamesTheRepCeilingWhenNoSetCanGiveOne() {
        val curl =
            benchProgress.copy(
                series =
                    mapOf(
                        ProgressMetric.TOP_SET to series("15", "15"),
                        ProgressMetric.ESTIMATED_ONE_REP_MAX to ProgressSeries(emptyList()),
                        ProgressMetric.VOLUME to series("675", "630"),
                    ),
                performances = listOf(benchPerformances.first().copy(workingSets = listOf(workingSet(9, 15.0, 15, rpe = null)))),
            )
        setScreen(
            loaded(metric = ProgressMetric.ESTIMATED_ONE_REP_MAX).copy(exercises = listOf(curl)),
        )

        composeRule.onNodeWithText(string(R.string.progress_estimate_needs_shorter_set)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_metric_empty)).assertDoesNotExist()
        // The `Best est. 1RM` record says the same with a dash (review P-4).
        composeRule.onNodeWithText(string(R.string.progress_record_estimate_none)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aRepsOnlyExerciseOffersTopSetAloneAndSaysWhy() {
        setScreen(loaded(selected = pullUps, metric = ProgressMetric.VOLUME))

        composeRule.onNodeWithText(string(R.string.progress_metric_top_set)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progress_metric_estimated_one_rep_max)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.progress_metric_volume)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.progress_load_metrics_unavailable_reps)).performScrollTo().assertIsDisplayed()
        assertEquals(
            "Est. 1RM and Volume need a load. This exercise is logged in reps.",
            string(R.string.progress_load_metrics_unavailable_reps),
        )
        composeRule.onNodeWithText(string(R.string.progress_delta_percent, "+3", "reps", "+50")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_caption_most_reps).uppercase()).assertIsDisplayed()
    }

    @Test
    fun aSingleSessionShowsItsValueAndReadoutButNoDelta() {
        setScreen(loaded(metric = ProgressMetric.ESTIMATED_ONE_REP_MAX))

        composeRule.onNodeWithText("88").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_readout_latest, "5 May")).assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("+", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.progress_metric_empty)).assertDoesNotExist()
    }

    @Test
    fun aRangeWithNoSessionKeepsThePillsAndSaysSo() {
        val old =
            benchProgress.copy(
                series =
                    mapOf(
                        ProgressMetric.TOP_SET to series("72.5", "75"),
                        ProgressMetric.ESTIMATED_ONE_REP_MAX to series("88"),
                        ProgressMetric.VOLUME to series("1740"),
                    ),
            )
        setScreen(loaded(range = ProgressRange.THREE_MONTHS).copy(exercises = listOf(old), now = Instant.parse("2027-01-01T12:00:00Z")))

        composeRule.onNodeWithText(string(R.string.progress_metric_empty)).assertIsDisplayed()
        composeRule.onNodeWithText("3m").assertIsSelected()
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithTag(PROGRESS_CHART_TAG).assertDoesNotExist()
    }

    @Test
    fun theTilesFrequencyAndRecordsDescribeTheChosenExercise() {
        setScreen(loaded())

        composeRule.onNodeWithText(string(R.string.progress_tile_sessions).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("8.3").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_frequency_title).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_tile_sessions_note)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_tile_avg_rpe_note)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_frequency_caption, "0.5")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_records_title).uppercase()).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_record_heaviest, "82.5 kg", 7)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_record_estimate, "99 kg")).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun noHistoryShowsTheTabsEmptyState() {
        setScreen(ProgressUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.progress_empty)).assertIsDisplayed()
    }

    /** Functional review GX-I2: `All` draws every session, and the date labels under it must not overlap. */
    @Test
    fun denseAllSeriesLabelsDoNotOverlapAt360dp() = assertDenseLabelsDoNotOverlap(widthDp = 360)

    @Test
    fun denseAllSeriesLabelsDoNotOverlapAt384dp() = assertDenseLabelsDoNotOverlap(widthDp = 384)

    private fun assertDenseLabelsDoNotOverlap(widthDp: Int) {
        val sessions = 70
        val start = Instant.parse("2025-01-06T12:00:00Z")
        val dense =
            benchProgress.copy(
                series =
                    mapOf(
                        ProgressMetric.TOP_SET to
                            ProgressSeries(
                                List(sessions) {
                                    ProgressPoint(
                                        WorkoutSessionId("d$it"),
                                        start.plus(Duration.ofDays(7L * it)),
                                        BigDecimal(60 + it),
                                    )
                                },
                            ),
                        ProgressMetric.ESTIMATED_ONE_REP_MAX to ProgressSeries(emptyList()),
                        ProgressMetric.VOLUME to ProgressSeries(emptyList()),
                    ),
            )
        composeRule.setContent {
            RepFlowTheme {
                Box(Modifier.width(widthDp.dp)) {
                    ProgressScreen(
                        uiState = loaded().copy(exercises = listOf(dense), now = start.plus(Duration.ofDays(7L * sessions))),
                        onExerciseSelected = {},
                        onMetricSelected = {},
                        onRangeSelected = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val labels =
            composeRule
                .onAllNodes(
                    SemanticsMatcher("a date label") {
                        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(PROGRESS_X_LABEL_TAG) ==
                            true
                    },
                ).fetchSemanticsNodes()
        val tags = labels.map { it.config[SemanticsProperties.TestTag] }
        assertTrue("the first label is shown", "${PROGRESS_X_LABEL_TAG}0" in tags)
        assertTrue("the last label is shown", "$PROGRESS_X_LABEL_TAG${sessions - 1}" in tags)
        val gapPx = 8 * composeRule.activity.resources.displayMetrics.density
        labels
            .map { it.boundsInRoot }
            .sortedBy { it.left }
            .zipWithNext()
            .forEach { (a, b) ->
                assertTrue("labels overlap or sit closer than 8dp: $a and $b", b.left - a.right >= gapPx - 2f)
            }
    }
}
