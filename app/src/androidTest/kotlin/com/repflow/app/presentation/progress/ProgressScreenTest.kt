package com.repflow.app.presentation.progress

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.application.progress.ProgressMetric
import com.repflow.app.application.progress.ProgressPoint
import com.repflow.app.application.progress.ProgressSeries
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

/**
 * Stateless Compose coverage for [ProgressScreen] on `4a`'s Progress tab
 * (remediation-1 CP15): the chips, the metric control, the card's value,
 * delta, chart and best line, the reps-only `Top set`-alone case (`D22`), the
 * fewer-than-two empty state, and the no-history empty state.
 */
@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val bench = ExerciseId("bench")
    private val pullUps = ExerciseId("pull-ups")

    /** Midday UTC, so the date reads the same in any device zone a test runs in. */
    private val may5: Instant = Instant.parse("2026-05-05T12:00:00Z")

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
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ProgressScreen(uiState = uiState, onExerciseSelected = onExerciseSelected, onMetricSelected = onMetricSelected)
            }
        }
    }

    private fun loaded(
        selected: ExerciseId = bench,
        metric: ProgressMetric = ProgressMetric.TOP_SET,
    ) = ProgressUiState(
        isLoading = false,
        exercises = listOf(benchProgress, pullUpProgress),
        selectedExerciseId = selected,
        selectedMetric = metric,
    )

    @Test
    fun theCardShowsTheLatestValueTheDeltaSinceTheWindowStartTheChartAndTheBest() {
        setScreen(loaded())

        composeRule.onNodeWithText("82.5").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta, "+10", "kg", "5 May")).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.resources.getQuantityString(
                    R.plurals.progress_chart_description,
                    4,
                    string(R.string.progress_metric_top_set),
                    4,
                    "72.5 kg",
                    "82.5 kg",
                ),
            ).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_best, "82.5", "kg", 4)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_valid_sessions_note)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theChipsAndSegmentsReportTheChoice() {
        var exercise: ExerciseId? = null
        var metric: ProgressMetric? = null
        setScreen(loaded(), onExerciseSelected = { exercise = it }, onMetricSelected = { metric = it })

        // The card repeats the selected exercise's name, so the chip is the selectable one.
        composeRule.onNode(hasText("Barbell Bench Press") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progress_metric_top_set)).assertIsSelected()

        composeRule.onNodeWithText("Pull-up").performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.progress_metric_volume)).performClick()

        assertEquals(pullUps, exercise)
        assertEquals(ProgressMetric.VOLUME, metric)
    }

    @Test
    fun volumeReadsInKilogramsTotal() {
        setScreen(loaded(metric = ProgressMetric.VOLUME))

        composeRule.onNodeWithText("2310").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_unit_kg_total)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta, "+570", "kg", "5 May")).assertIsDisplayed()
    }

    @Test
    fun aRepsOnlyExerciseOffersTopSetAloneAndSaysWhy() {
        setScreen(loaded(selected = pullUps, metric = ProgressMetric.VOLUME))

        composeRule.onNodeWithText(string(R.string.progress_metric_top_set)).assertIsSelected()
        composeRule.onNodeWithText(string(R.string.progress_metric_estimated_one_rep_max)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.progress_metric_volume)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.progress_load_metrics_unavailable)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_delta, "+3", "reps", "5 May")).assertIsDisplayed()
    }

    @Test
    fun anOfferedMetricWithOnePointShowsTheEmptyStateInsteadOfAChart() {
        setScreen(loaded(metric = ProgressMetric.ESTIMATED_ONE_REP_MAX))

        composeRule.onNodeWithText("88").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_metric_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progress_load_metrics_unavailable)).assertDoesNotExist()
    }

    @Test
    fun noHistoryShowsTheTabsEmptyState() {
        setScreen(ProgressUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.progress_empty)).assertIsDisplayed()
    }
}
