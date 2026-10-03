package com.repflow.app.presentation.history

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.history.BestSet
import com.repflow.app.application.history.ExerciseRecap
import com.repflow.app.application.history.WorkoutSummary
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Stateless Compose coverage for [HistoryDetailScreen] - surfacing every
 * recordable set field (Milestone 8, CP13), on `3b`'s converted rows
 * (remediation-1 CP12): a timed set reads as its seconds, never the old
 * `Set 0:  kg x ` template, and an exercise with no sets reads `No sets
 * logged` with no change shown (`D20`).
 */
@RunWith(AndroidJUnit4::class)
class HistoryDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun exercise(
        id: String,
        name: String,
        trackingType: ExerciseTrackingType,
        sets: List<WorkoutSet>,
        order: Int = 0,
    ): WorkoutExercise =
        (
            WorkoutExercise.create(
                id = WorkoutExerciseId(id),
                sessionId = WorkoutSessionId("session-1"),
                exerciseId = ExerciseId("ex-$id"),
                order = order,
                exerciseNameSnapshot = name,
                trackingType = trackingType,
                plannedExerciseId = null,
                sets = sets,
            ) as DomainResult.Success
        ).value

    private fun sessionWith(vararg exercises: WorkoutExercise): WorkoutSession {
        val started = WorkoutSession.start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
        val withExercises =
            exercises.fold(started) { session, exercise -> (session.withAddedExercise(exercise) as DomainResult.Success).value }
        return (withExercises.complete(Instant.parse("2026-01-01T01:00:00Z")) as DomainResult.Success).value
    }

    private fun sessionWithSet(
        set: WorkoutSet,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    ): WorkoutSession = sessionWith(exercise("we-1", "Bench Press", trackingType, listOf(set)))

    /** A summary with no history to compare against: no change is shown for any exercise. */
    private fun summaryOf(session: WorkoutSession): WorkoutSummary =
        WorkoutSummary(session = session, recaps = session.exercises.map { ExerciseRecap(it, null, null) }, personalBests = emptyList())

    private fun setDetail(
        summary: WorkoutSummary,
        onBackClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                HistoryDetailScreen(
                    summary = summary,
                    planLabel = null,
                    onBackClick = onBackClick,
                    onInvalidateConfirmed = {},
                )
            }
        }
    }

    /** At 1.3x and 2.0x font the Volume tile's caption wraps instead of ellipsizing, so `(KG)` stays visible (P2-F-4). */
    @Test
    fun theVolumeCaptionKeepsItsUnitAtLargeFontSizes() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                ) as DomainResult.Success
            ).value
        val summary = summaryOf(sessionWithSet(set))
        val scale = mutableStateOf(LARGE_FONT_SCALE_MEDIUM)
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, scale.value)) {
                RepFlowTheme {
                    HistoryDetailScreen(summary = summary, planLabel = null, onBackClick = {}, onInvalidateConfirmed = {})
                }
            }
        }
        listOf(LARGE_FONT_SCALE_MEDIUM, LARGE_FONT_SCALE_MAX).forEach { fontScale ->
            composeRule.runOnIdle { scale.value = fontScale }
            composeRule.waitForIdle()
            val results = mutableListOf<TextLayoutResult>()
            val node = composeRule.onNodeWithText("VOLUME (KG)", useUnmergedTree = true)
            node.assertIsDisplayed()
            node
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action
                ?.invoke(results)
            val layout = results.single()
            val ellipsized = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
            assertEquals("caption is ellipsized at font $fontScale", false, ellipsized)
        }
    }

    @Test
    fun rendersRpeDurationWarmupPainAndTechniqueQuality() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = 8.5,
                    isWarmup = true,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = 2,
                    techniqueQuality = 4,
                ) as DomainResult.Success
            ).value
        setDetail(summaryOf(sessionWithSet(set)))

        // Exact match: the exercise's own `1 warm-up set` line also contains the word.
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_warmup_suffix))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.resources.getQuantityString(R.plurals.history_detail_warmup_count, 1, 1))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_rpe, "8.5"))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_pain, 2))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_technique_quality, 4))
            .assertIsDisplayed()
    }

    @Test
    fun rendersDurationForADurationTrackedSet() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.DURATION,
                    load = null,
                    reps = null,
                    durationSeconds = 45,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = null,
                    techniqueQuality = null,
                ) as DomainResult.Success
            ).value
        setDetail(summaryOf(sessionWithSet(set, ExerciseTrackingType.DURATION)))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_duration, 45))
            .assertIsDisplayed()
    }

    /**
     * The parent milestone's recorded defect: a DURATION set used to render
     * `Set 0:  kg x ` - the load-and-reps template with nothing to fill it.
     * The converted row picks its words by tracking type, so no `kg` and no
     * `×` appear anywhere for a timed set.
     */
    @Test
    fun aDurationSetHasNoLoadAndRepsTemplate() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.DURATION,
                    load = null,
                    reps = null,
                    durationSeconds = 60,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = null,
                    techniqueQuality = null,
                ) as DomainResult.Success
            ).value
        setDetail(summaryOf(sessionWithSet(set, ExerciseTrackingType.DURATION)))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_duration, 60))
            .assertIsDisplayed()
        composeRule.onNodeWithText("kg", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("×", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(" x ", substring = true).assertDoesNotExist()
    }

    /**
     * `D20`: an exercise with no sets reads `No sets logged`, never the
     * design's `skipped`, and shows no change against last time - even when
     * the summary knows a last time for it - while a trained exercise beside
     * it still shows its own.
     */
    @Test
    fun aZeroSetExerciseShowsNoSetsLoggedAndNoDelta() {
        val trainedSet =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 82.5,
                    reps = 5,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = null,
                    techniqueQuality = null,
                ) as DomainResult.Success
            ).value
        val trained = exercise("we-1", "Bench Press", ExerciseTrackingType.WEIGHT_AND_REPS, listOf(trainedSet))
        val empty = exercise("we-2", "Barbell Row", ExerciseTrackingType.WEIGHT_AND_REPS, emptyList(), order = 1)
        val session = sessionWith(trained, empty)
        val summary =
            WorkoutSummary(
                session = session,
                recaps =
                    listOf(
                        ExerciseRecap(session.exercises[0], best = BestSet.Load(82.5, 5), lastTime = BestSet.Load(80.0, 5)),
                        ExerciseRecap(session.exercises[1], best = null, lastTime = BestSet.Load(70.0, 8)),
                    ),
                personalBests = emptyList(),
            )
        setDetail(summary)

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_no_sets))
            .assertIsDisplayed()
        composeRule.onNodeWithText("skipped", substring = true, ignoreCase = true).assertDoesNotExist()
        // The trained exercise's change renders; it is the only change on the screen.
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_done_delta_load_up, "2.5"))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("+", substring = true).assertCountEquals(1)
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_done_delta_first))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_done_delta_load_down, "2.5"), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun backButtonInvokesOnBackClick() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = null,
                    techniqueQuality = null,
                ) as DomainResult.Success
            ).value
        var backClicked = false
        setDetail(summaryOf(sessionWithSet(set)), onBackClick = { backClicked = true })

        // CP3's sub-screen bar keeps the label as the back arrow's content description.
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.history_detail_back))
            .performClick()

        assertEquals(true, backClicked)
    }

    /** Functional review J7: system Back closes the detail, as the bar's arrow does. */
    @Test
    fun systemBackInvokesOnBackClick() {
        val set =
            (
                WorkoutSet.create(
                    id = WorkoutSetId("set-1"),
                    order = 0,
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    load = 60.0,
                    reps = 8,
                    durationSeconds = null,
                    rpe = null,
                    isWarmup = false,
                    createdAt = Instant.parse("2026-01-01T00:10:00Z"),
                    updatedAt = Instant.parse("2026-01-01T00:10:00Z"),
                    pain = null,
                    techniqueQuality = null,
                ) as DomainResult.Success
            ).value
        var backClicked = false
        setDetail(summaryOf(sessionWithSet(set)), onBackClick = { backClicked = true })

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(true, backClicked)
    }
}

private const val LARGE_FONT_SCALE_MEDIUM = 1.3f
private const val LARGE_FONT_SCALE_MAX = 2.0f
