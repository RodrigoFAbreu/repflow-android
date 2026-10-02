package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import com.repflow.app.presentation.progression.ProgressionResultUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Stateless Compose coverage (Milestone 8, implementation-review findings
 * #2/#3) for [ActiveWorkoutScreen]'s per-exercise field visibility and
 * recorded-set formatting: only the field(s) each [ExerciseTrackingType]
 * actually persists are shown, and a recorded set's summary never stands
 * in a misleading `0`/`0.0` for a genuinely-absent value.
 *
 * The content is wrapped in [RepFlowTheme] so these tests render the same
 * scheme/token combination production does. Without it the screen would take
 * Material 3's baseline scheme while `LocalRepFlowExtraColors` had no
 * provider at all - a combination that cannot occur in the app, and one the
 * local's failing default now rejects outright.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Renders the workout surface with [exercises] on the board. By default
     * the first exercise's set entry is open - the surface every test before
     * remediation-1 CP7 rendered directly, and which the board now opens per
     * exercise; pass `openFirstExercise = false` to see the board itself.
     */
    private fun setContent(
        exercise: ActiveExerciseUi,
        onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit = { _, _, _, _, _, _, _, _ -> },
        availableExercises: List<ExercisePickerItem> = emptyList(),
        onOpenRecommendation: (ExerciseId) -> Unit = {},
        openFirstExercise: Boolean = true,
        exercises: List<ActiveExerciseUi> = listOf(exercise),
        onFocusExercise: (WorkoutExerciseId?) -> Unit = {},
        onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit = { _, _, _, _, _, _, _, _ -> },
        onCompleteWorkout: (WorkoutSessionId) -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ActiveWorkoutScreen(
                    uiState =
                        ActiveWorkoutUiState(
                            content =
                                ActiveWorkoutContent.Active(
                                    sessionId = WorkoutSessionId("session-1"),
                                    startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                                    exercises = exercises,
                                ),
                            availableExercises = availableExercises,
                        ),
                    dayContext = null,
                    focusedExerciseId = if (openFirstExercise) exercises.firstOrNull()?.id else null,
                    onFocusExercise = onFocusExercise,
                    onAddExercise = {},
                    onCreateExercise = {},
                    onOpenRecommendation = onOpenRecommendation,
                    onRecordSet = onRecordSet,
                    onUndoLastSet = {},
                    onEditLastSet = onEditLastSet,
                    onAddRestTime = {},
                    onRemoveRestTime = {},
                    onSkipRestTimer = {},
                    onCompleteWorkout = onCompleteWorkout,
                    onLeaveWorkout = {},
                    onAbandonWorkout = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun exercise(
        trackingType: ExerciseTrackingType,
        sets: List<ActiveSetUi> = emptyList(),
        plannedTarget: PlannedTargetUi? = null,
        exerciseId: ExerciseId? = null,
        instructions: String? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId("exercise-1"),
        name = "Bench Press",
        trackingType = trackingType,
        sets = sets,
        plannedTarget = plannedTarget,
        exerciseId = exerciseId,
        instructions = instructions,
    )

    private fun set(
        setNumber: Int,
        load: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        isWarmup: Boolean = false,
        rpe: Double? = null,
        pain: Int? = null,
    ) = ActiveSetUi(
        id = WorkoutSetId("set-$setNumber"),
        setNumber = setNumber,
        load = load,
        reps = reps,
        durationSeconds = durationSeconds,
        isWarmup = isWarmup,
        rpe = rpe,
        pain = pain,
    )

    private fun plural(
        id: Int,
        quantity: Int,
        vararg args: Any,
    ) = composeRule.activity.resources.getQuantityString(id, quantity, *args)

    @Test
    fun weightAndRepsExerciseShowsLoadAndRepsFieldsButNotDuration() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS))

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_load_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_reps_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_duration_label)).assertDoesNotExist()
    }

    @Test
    fun repsOnlyExerciseShowsOnlyTheRepsField() {
        setContent(exercise(ExerciseTrackingType.REPS_ONLY))

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_reps_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_load_label)).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_duration_label)).assertDoesNotExist()
    }

    @Test
    fun durationExerciseShowsOnlyTheDurationField() {
        setContent(exercise(ExerciseTrackingType.DURATION))

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_duration_label)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_load_label)).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_reps_label)).assertDoesNotExist()
    }

    @Test
    fun aDurationSetSummaryShowsItsDurationNotAMisleadingZeroLoadRow() {
        setContent(exercise(ExerciseTrackingType.DURATION, sets = listOf(set(1, durationSeconds = 45))))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_duration, 1, 45))
            .assertIsDisplayed()
    }

    @Test
    fun aWeightAndRepsSetWithoutARecordedLoadShowsTheRepsOnlyFormatInsteadOfAZeroLoad() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, sets = listOf(set(1, reps = 12))))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_reps, 1, 12))
            .assertIsDisplayed()
    }

    /**
     * Remediation-1 CP8 rewrote this against focus mode: the planned-target
     * chips are gone, and warm-up and working progress are the header's
     * `X of Y sets done` - with the plan's warm-ups beside it - while each
     * planned set not logged yet is a row of its own carrying the target.
     */
    @Test
    fun aPlannedExerciseShowsWarmupAndWorkingProgress() {
        val target = PlannedTargetUi(targetWarmupSets = 2, targetWorkingSets = 3, repRange = 8..12, restSeconds = 60)
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = target))

        composeRule
            .onNodeWithText(
                plural(R.plurals.workout_focus_sets_done, 3, 0, 3) + " · " + plural(R.plurals.workout_focus_warmups_done, 2, 0, 2),
            ).assertIsDisplayed()
        val firstPendingRow =
            listOf(
                composeRule.activity.getString(R.string.workout_focus_set_row_pending, 1),
                composeRule.activity.getString(R.string.workout_active_plan_rep_range, 8, 12),
                composeRule.activity.getString(R.string.workout_active_plan_rest, 60),
            ).joinToString(separator = " · ")
        composeRule.onNodeWithText(firstPendingRow).performScrollTo().assertIsDisplayed()
        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.workout_focus_set_row_pending, 3), substring = true)
            .assertCountEquals(1)
    }

    /**
     * The parent milestone's chip-row clipping guard, **retargeted** by
     * remediation-1 CP8 rather than retired: the rep range, duration range
     * and rest the old chips carried now ride on the not-yet-logged set row,
     * and that row must still degrade visibly rather than push a value off
     * the right edge - the silent clipping the parent's round-1 fix existed to
     * prevent. Same method as before: values deliberately absurd, so nothing
     * fits at any phone width, then every value must be on screen. The row is
     * one wrapping line, so this also pins that it stays inside the root's
     * width.
     */
    @Test
    fun everyPlannedTargetValueStaysOnScreenWhenThePendingRowOutgrowsTheWidth() {
        val target =
            PlannedTargetUi(
                targetWarmupSets = null,
                targetWorkingSets = 1,
                repRange = 100_000_000..999_999_999,
                durationRangeSeconds = 100_000_000L..999_999_999L,
                restSeconds = 999_999_999,
            )
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = target))

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().width
        listOf(
            composeRule.activity.getString(R.string.workout_active_plan_rep_range, 100_000_000, 999_999_999),
            composeRule.activity.getString(R.string.workout_active_plan_duration_range, 100_000_000L, 999_999_999L),
            composeRule.activity.getString(R.string.workout_active_plan_rest, 999_999_999),
        ).forEach { value ->
            val row = composeRule.onNodeWithText(value, substring = true)
            row.performScrollTo().assertIsDisplayed()
            val bounds = row.getUnclippedBoundsInRoot()
            assertTrue("The pending row ends at ${bounds.right}, past the root's $rootWidth", bounds.right <= rootWidth)
        }
    }

    /**
     * Remediation-1 CP8: with the target chips gone for every exercise, the
     * old "no working-progress chip" assertion would pass vacuously. What
     * distinguishes an ad-hoc exercise on focus mode is that it has no
     * target: its header only counts (`D55`) and it has no not-yet-logged
     * rows - both asserted positively.
     */
    @Test
    fun anAdHocExerciseShowsNoPlannedTargetSummary() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = null))

        composeRule.onNodeWithText(plural(R.plurals.workout_focus_sets_logged, 0, 0)).assertIsDisplayed()
        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.workout_focus_set_row_pending, 1))
            .assertCountEquals(0)
    }

    @Test
    fun aSetBeyondThePlannedWorkingCountIsMarkedExtra() {
        val target = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 1, repRange = 8..12)
        val sets = listOf(set(1, load = 60.0, reps = 10), set(2, load = 60.0, reps = 8))
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, sets = sets, plannedTarget = target))

        val secondRowText =
            composeRule.activity.getString(R.string.workout_active_set_row_weight_reps, 2, 60.0, 8) +
                " " + composeRule.activity.getString(R.string.workout_active_set_extra_suffix)
        composeRule.onNodeWithText(secondRowText).assertIsDisplayed()
    }

    /*
     * The set-detail disclosure gates the three optional inputs - RPE, pain
     * and technique, scale rows since remediation-1 CP8 replaced the text
     * fields. Three tests below are about the disclosure and survive that
     * swap unchanged: what it hides, that opening it reveals all three rows
     * (located by their labels), and that the header node a screen reader
     * activates reads differently in the two states. The fourth is about the
     * values it reveals and was rewritten by CP8 to drive the scale rows: a
     * value chosen in them reaches `onRecordSet` whether the section is open
     * or closed at submit time (the retention property `SetDetailSection`'s
     * KDoc claims).
     */

    private fun node(
        @StringRes label: Int,
    ) = composeRule.onNodeWithText(composeRule.activity.getString(label))

    private fun hasStateDescription(
        @StringRes value: Int,
    ) = SemanticsMatcher.expectValue(
        SemanticsProperties.StateDescription,
        composeRule.activity.getString(value),
    )

    @Test
    fun theSetDetailFieldsAreHiddenUntilTheDisclosureIsExpanded() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS))

        node(R.string.workout_active_set_detail_toggle).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_rpe_label).assertDoesNotExist()
        node(R.string.workout_active_pain_label).assertDoesNotExist()
        node(R.string.workout_active_technique_quality_label).assertDoesNotExist()
    }

    @Test
    fun expandingTheDisclosureRevealsAllThreeOptionalFields() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS))

        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()

        node(R.string.workout_active_rpe_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_pain_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_technique_quality_label).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aValueTypedIntoTheExpandedDetailFieldsReachesOnRecordSetEvenAfterCollapsing() {
        var recordedRpe: Double? = null
        var recordedPain: Int? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            onRecordSet = { _, _, _, _, rpe, _, pain, _ ->
                recordedRpe = rpe
                recordedPain = pain
            },
        )

        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()
        // RPE's row is the only one with an 8; pain is the second of the three rows with a 2.
        composeRule.onNode(hasText("8") and isSelectable()).performScrollTo().performClick()
        composeRule.onAllNodes(hasText("2") and isSelectable())[1].performScrollTo().performClick()
        // Collapse again before submitting: the values live in focus mode's own
        // entry state, not in the section, so hiding them must not drop them.
        composeRule
            .onNode(hasStateDescription(R.string.workout_active_set_detail_expanded))
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.workout_focus_detail_rpe, 8) + " · " +
                    composeRule.activity.getString(R.string.workout_focus_detail_pain, 2),
            ).assertIsDisplayed()
        node(R.string.workout_focus_log_set).performClick()

        assertEquals(8.0, recordedRpe)
        assertEquals(2, recordedPain)
    }

    @Test
    fun theDisclosureHeaderReadsItsExpandedStateToAccessibilityServices() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS))

        // `clickable` merges the header's descendants into one node and both
        // glyphs are `contentDescription = null`, so the caret cannot carry
        // this: without a state description the node reads identically in
        // both states and activating it announces nothing.
        node(R.string.workout_active_set_detail_toggle)
            .performScrollTo()
            .assert(hasStateDescription(R.string.workout_active_set_detail_collapsed))

        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()

        node(R.string.workout_active_set_detail_toggle)
            .performScrollTo()
            .assert(hasStateDescription(R.string.workout_active_set_detail_expanded))
    }

    /**
     * Rewritten by remediation-1 CP8 to drive the weight stepper and its
     * keypad, since a stepper cannot take `performTextInput`. The contract is
     * unchanged: logging a set clears what was entered, so the next set starts
     * fresh (`D60`).
     */
    @Test
    fun tappingAddSetClearsTheEntryFields() {
        var recordedLoad: Double? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            onRecordSet = { _, load, _, _, _, _, _, _ -> recordedLoad = load },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_weight))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("2.5").performScrollTo().performClick()
        listOf("6", "0").forEach { key -> composeRule.onNodeWithText(key).performClick() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_confirm)).performClick()
        composeRule.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        node(R.string.workout_focus_log_set).performClick()

        assertEquals(60.0, recordedLoad)
        composeRule.onNodeWithText("60").assertDoesNotExist()
    }

    /**
     * The picker row's way into the recommendation screen (remediation-1 CP6):
     * the row keeps its summary and gains `Why ›`, which hands out that row's
     * exercise; the three inline override buttons it used to carry are gone,
     * because the override is now recorded on the recommendation screen. The
     * route-level half - `Why ›` actually reaching the screen through the nav
     * graph - is `ProgressionRecommendationRouteTest`'s.
     */
    @Test
    fun thePickerRowsWhyOpensTheRecommendationForItsExercise() {
        val squat = ExerciseId("exercise-squat")
        var opened: ExerciseId? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            availableExercises =
                listOf(
                    ExercisePickerItem(
                        id = squat,
                        name = "Back Squat",
                        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                        recommendation =
                            ProgressionRecommendationUi(
                                result = ProgressionResultUi.INCREASE_LOAD,
                                topReason = "Every working set reached 8+ reps",
                                isOverridden = false,
                            ),
                    ),
                ),
            onOpenRecommendation = { opened = it },
            openFirstExercise = false,
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_add_exercise)).performScrollTo().performClick()
        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.progression_result_increase_load) + " — Every working set reached 8+ reps",
            ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.progression_result_maintain_load)).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.progression_result_reduce_load)).assertDoesNotExist()

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.progression_why_content_description, "Back Squat"))
            .performClick()

        assertEquals(squat, opened)
    }

    // ---- The workout board (remediation-1 CP7) ----

    private fun boardExercise(
        id: String,
        name: String,
        sets: List<ActiveSetUi> = emptyList(),
        targetWorkingSets: Int? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId(id),
        name = name,
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        sets = sets,
        plannedTarget = targetWorkingSets?.let { PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = it) },
    )

    /**
     * Plan CP7 item 7's state test: a board row has **one** action - opening
     * the exercise's set entry - and no overflow trigger or row-sheet option,
     * because none of the design's six row-sheet options has domain backing
     * (`D1`-`D3`, `D13`-`D15`). The labels are the design's own
     * (`RepFlow.dc.html:1213`, `:1689-1703`); none may exist anywhere.
     */
    @Test
    fun aBoardRowOpensItsSetEntryAndExposesNoOverflowOrRowSheet() {
        var focused: WorkoutExerciseId? = null
        val bench = boardExercise("exercise-1", "Bench Press", targetWorkingSets = 3)
        setContent(bench, openFirstExercise = false, onFocusExercise = { focused = it })

        composeRule.onAllNodes(hasClickAction() and hasText("Bench Press")).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Exercise options").assertDoesNotExist()
        listOf(
            "Do this later",
            "Swap for another exercise",
            "Superset with the next exercise",
            "Skip for today",
            "Add a note",
            "Remove from this workout",
        ).forEach { composeRule.onNodeWithText(it, substring = true).assertDoesNotExist() }

        composeRule.onNodeWithText("Bench Press").performClick()
        assertEquals(bench.id, focused)
    }

    /**
     * The progress line and the three status-chip states, each a word (`6b`:
     * never colour alone), with `up next` on the first unfinished exercise -
     * which need not be the first row: out-of-order work is the point of the
     * board. Warm-ups never count towards a target.
     */
    @Test
    fun theBoardShowsProgressEachRowsStatusAndUpNext() {
        val warmup = set(1, load = 40.0, reps = 10, isWarmup = true)
        val exercises =
            listOf(
                boardExercise("e1", "Bench Press", sets = listOf(set(1, 60.0, 8), set(2, 60.0, 8)), targetWorkingSets = 2),
                boardExercise("e2", "Row", sets = listOf(warmup, set(2, 50.0, 10)), targetWorkingSets = 3),
                boardExercise("e3", "Curl", targetWorkingSets = 4),
            )
        setContent(exercises.first(), exercises = exercises, openFirstExercise = false)

        composeRule.onNodeWithText(plural(R.plurals.workout_board_progress, 3, 1, 3, 3, 9)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_board_status_progress, 2, 2, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_board_status_progress, 3, 3, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_board_status_target, 4, 4)).assertIsDisplayed()
        composeRule
            .onAllNodes(
                hasText(composeRule.activity.getString(R.string.workout_board_up_next), ignoreCase = true),
            ).assertCountEquals(1)
        composeRule
            .onNode(
                hasClickAction() and hasText("Row") and
                    hasText(composeRule.activity.getString(R.string.workout_board_up_next), ignoreCase = true),
            ).assertIsDisplayed()
    }

    /** `4a`'s empty board: the clock is already running; `Add exercise` is still there. */
    @Test
    fun anEmptyBoardShowsTheDesignsEmptyStateAndAddExercise() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS), exercises = emptyList(), openFirstExercise = false)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_board_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_board_empty_body)).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.workout_active_add_exercise),
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_untitled_workout)).assertIsDisplayed()
    }

    // ---- Focus mode (remediation-1 CP8) ----

    private fun benchPickerItem(recommendation: ProgressionRecommendationUi?) =
        ExercisePickerItem(
            id = ExerciseId("exercise-bench"),
            name = "Bench Press",
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            recommendation = recommendation,
        )

    /**
     * Plan CP8 item 5's state test, first half: when the exercise has a
     * recommendation, the suggestion strip shows the proposed action and the
     * policy's top reason, and its `Why ›` hands out this exercise - the
     * route-level half, `Why ›` reaching CP6's screen through the graph, is
     * `ProgressionRecommendationRouteTest`'s.
     */
    @Test
    fun theSuggestionStripShowsTheRecommendationAndItsWhyOpensIt() {
        var opened: ExerciseId? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS, exerciseId = ExerciseId("exercise-bench")),
            availableExercises =
                listOf(
                    benchPickerItem(
                        ProgressionRecommendationUi(
                            result = ProgressionResultUi.INCREASE_LOAD,
                            topReason = "Every working set reached 8+ reps",
                            isOverridden = false,
                        ),
                    ),
                ),
            onOpenRecommendation = { opened = it },
        )

        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.progression_result_increase_load) + " — Every working set reached 8+ reps",
            ).performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.progression_why_content_description, "Bench Press"))
            .performScrollTo()
            .performClick()

        assertEquals(ExerciseId("exercise-bench"), opened)
    }

    /** Plan CP8 item 5's state test, second half: no recommendation, no strip and no `Why`. */
    @Test
    fun theSuggestionStripIsAbsentWhenThereIsNoRecommendation() {
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS, exerciseId = ExerciseId("exercise-bench")),
            availableExercises = listOf(benchPickerItem(recommendation = null)),
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.progression_why_content_description, "Bench Press"))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.progression_why_link)).assertDoesNotExist()
    }

    /** Plan CP8 item 11: the exercise's technique notes are a collapsed row under the header. */
    @Test
    fun techniqueNotesAreACollapsedRowThatRevealsTheExercisesInstructions() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, instructions = "Keep the bar over mid-foot."))

        composeRule.onNodeWithText("Keep the bar over mid-foot.").assertDoesNotExist()
        node(R.string.workout_focus_technique_notes).performScrollTo().performClick()
        composeRule.onNodeWithText("Keep the bar over mid-foot.").performScrollTo().assertIsDisplayed()
    }

    /** Plan CP8 item 11: absent entirely when the exercise has no instructions. */
    @Test
    fun techniqueNotesAreAbsentWhenTheExerciseHasNone() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, instructions = null))

        node(R.string.workout_focus_technique_notes).assertDoesNotExist()
    }

    /** `Next ›` skips finished exercises and opens the next unfinished one in board order. */
    @Test
    fun nextOpensTheNextUnfinishedExercise() {
        var focused: WorkoutExerciseId? = null
        val exercises =
            listOf(
                boardExercise("e1", "Bench Press", targetWorkingSets = 2),
                boardExercise("e2", "Row", sets = listOf(set(1, 50.0, 10)), targetWorkingSets = 1),
                boardExercise("e3", "Curl", targetWorkingSets = 3),
            )
        setContent(exercises.first(), exercises = exercises, onFocusExercise = { focused = it })

        node(R.string.workout_focus_next).performClick()

        assertEquals(WorkoutExerciseId("e3"), focused)
    }

    /**
     * The pencil on the most recently logged set opens the correction sheet
     * (`D31`, `D66`): it corrects what was lifted through `onEditLastSet` and
     * hands the set's RPE, pain and warm-up flag back unchanged.
     */
    @Test
    fun correctingTheLastSetKeepsItsOtherFieldsAndReachesOnEditLastSet() {
        var edited: List<Any?>? = null
        setContent(
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = listOf(set(1, load = 60.0, reps = 8, rpe = 7.5, pain = 1)),
            ),
            onEditLastSet = { _, load, reps, duration, rpe, isWarmup, pain, technique ->
                edited = listOf(load, reps, duration, rpe, isWarmup, pain, technique)
            },
        )

        composeRule
            .onNode(hasClickAction() and hasText(composeRule.activity.getString(R.string.workout_active_set_row_weight_reps, 1, 60.0, 8)))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_focus_correct_title, 1)).assertIsDisplayed()
        composeRule
            .onAllNodesWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_reps))
            .onLast()
            .performClick()
        node(R.string.workout_focus_correct_save).performClick()

        assertEquals(listOf(60.0, 9, null, 7.5, false, 1, null), edited)
    }

    // ---- The finish sheet (remediation-1 CP9) ----

    /**
     * Plan CP9 item 1: the board's `Finish` raises the one finish sheet - it
     * completes nothing itself - and the sheet lists what is still unfinished
     * by the board's own rule (`N sets left` for a planned exercise, `No sets
     * yet` for an ad-hoc one with nothing logged; a finished one is absent).
     * Only the sheet's confirm reaches `onCompleteWorkout`.
     */
    @Test
    fun theBoardsFinishRaisesTheSheetWhichListsUnfinishedWorkAndOnlyItsConfirmCompletes() {
        val completed = mutableListOf<WorkoutSessionId>()
        val exercises =
            listOf(
                boardExercise("e1", "Bench Press", sets = listOf(set(1, 60.0, 8)), targetWorkingSets = 1),
                boardExercise("e2", "Row", sets = listOf(set(1, 50.0, 10)), targetWorkingSets = 3),
                boardExercise("e3", "Curl"),
            )
        setContent(exercises.first(), exercises = exercises, openFirstExercise = false, onCompleteWorkout = { completed += it })

        node(R.string.workout_board_finish).performClick()

        node(R.string.workout_finish_title).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_finish_unfinished_count, 2, 2)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_finish_sets_left, 2, 2)).assertIsDisplayed()
        // `Curl` reads `No sets yet` twice: its board chip, and its row in the sheet.
        composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.workout_board_status_none)).assertCountEquals(2)
        composeRule.onAllNodesWithText("Bench Press").assertCountEquals(1)
        assertTrue("Finish completes nothing before the sheet's confirm", completed.isEmpty())

        node(R.string.workout_finish_keep_training).performClick()
        node(R.string.workout_finish_title).assertDoesNotExist()
        assertTrue(completed.isEmpty())

        node(R.string.workout_board_finish).performClick()
        node(R.string.workout_finish_confirm).performClick()
        assertEquals(listOf(WorkoutSessionId("session-1")), completed)
    }

    /** Focus mode's `Next ›` on the last unfinished exercise returns to the board with the finish sheet raised (`nNextExercise`). */
    @Test
    fun nextWithNothingUnfinishedLeftReturnsToTheBoardWithTheFinishSheetRaised() {
        val focusRequests = mutableListOf<WorkoutExerciseId?>()
        val exercises = listOf(boardExercise("e1", "Bench Press", sets = listOf(set(1, 60.0, 8)), targetWorkingSets = 1))
        setContent(exercises.first(), exercises = exercises, onFocusExercise = { focusRequests += it })

        node(R.string.workout_focus_next).performClick()

        assertEquals(listOf<WorkoutExerciseId?>(null), focusRequests)
        node(R.string.workout_finish_title).assertIsDisplayed()
    }

    /**
     * Plan CP8 item 9: on the last unfinished exercise - the focused one still
     * has sets pending and every other exercise is finished - `Next ›` returns
     * to the board with the finish sheet raised, which names that exercise's
     * remaining sets. It completes nothing by itself.
     */
    @Test
    fun nextOnTheLastUnfinishedExerciseReturnsToTheBoardWithTheFinishSheetRaised() {
        val focusRequests = mutableListOf<WorkoutExerciseId?>()
        val completed = mutableListOf<WorkoutSessionId>()
        val exercises =
            listOf(
                boardExercise("e1", "Bench Press", sets = listOf(set(1, 60.0, 8)), targetWorkingSets = 3),
                boardExercise("e2", "Row", sets = listOf(set(1, 50.0, 10)), targetWorkingSets = 1),
            )
        setContent(
            exercises.first(),
            exercises = exercises,
            onFocusExercise = { focusRequests += it },
            onCompleteWorkout = { completed += it },
        )

        node(R.string.workout_focus_next).performClick()

        assertEquals(listOf<WorkoutExerciseId?>(null), focusRequests)
        node(R.string.workout_finish_title).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_finish_unfinished_count, 1, 1)).assertIsDisplayed()
        composeRule.onNodeWithText(plural(R.plurals.workout_finish_sets_left, 2, 2)).assertIsDisplayed()
        assertTrue("Next completes nothing before the sheet's confirm", completed.isEmpty())
    }

    /** The leave sheet's `Finish and save it now` raises the finish sheet rather than completing anything itself. */
    @Test
    fun theLeaveSheetsFinishNowRaisesTheFinishSheet() {
        val completed = mutableListOf<WorkoutSessionId>()
        val exercises = listOf(boardExercise("e1", "Bench Press", targetWorkingSets = 2))
        setContent(exercises.first(), exercises = exercises, openFirstExercise = false, onCompleteWorkout = { completed += it })

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.workout_board_leave_content_description),
            ).performClick()
        node(R.string.workout_leave_finish_now).performClick()

        node(R.string.workout_finish_title).assertIsDisplayed()
        assertTrue(completed.isEmpty())
    }
}
