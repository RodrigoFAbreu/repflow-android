package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.application.workout.LastPerformance
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.assertButtonLabelWhole
import com.repflow.app.presentation.designsystem.components.formatDuration
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import com.repflow.app.presentation.progression.ProgressionResultUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
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
        onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?, () -> Unit) -> Unit =
            { _, _, _, _, _, _, _, _, _ -> },
        availableExercises: List<ExercisePickerItem> = emptyList(),
        onOpenRecommendation: (ExerciseId) -> Unit = {},
        openFirstExercise: Boolean = true,
        exercises: List<ActiveExerciseUi> = listOf(exercise),
        onFocusExercise: (WorkoutExerciseId?) -> Unit = {},
        onEditSet: (WorkoutExerciseId, WorkoutSetId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit =
            { _, _, _, _, _, _, _, _, _ -> },
        onUndoLastSet: (WorkoutExerciseId) -> Unit = {},
        onCompleteWorkout: (WorkoutSessionId) -> Unit = {},
        errorMessage: State<ActiveWorkoutErrorReason?> = mutableStateOf(null),
        onErrorShown: () -> Unit = {},
        restTimer: RestTimerUi? = null,
        extraSetFields: ExtraSetFields = ExtraSetFields.DEFAULT,
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
                                    restTimer = restTimer,
                                    extraSetFields = extraSetFields,
                                ),
                            availableExercises = availableExercises,
                            errorMessage = errorMessage.value,
                        ),
                    dayContext = null,
                    focusedExerciseId = if (openFirstExercise) exercises.firstOrNull()?.id else null,
                    onFocusExercise = onFocusExercise,
                    onAddExercise = {},
                    onCreateExercise = {},
                    onOpenRecommendation = onOpenRecommendation,
                    onRecordSet = onRecordSet,
                    onUndoLastSet = onUndoLastSet,
                    onEditSet = onEditSet,
                    onAddRestTime = {},
                    onRemoveRestTime = {},
                    onSkipRestTimer = {},
                    onCompleteWorkout = onCompleteWorkout,
                    onLeaveWorkout = {},
                    onAbandonWorkout = {},
                    onRetry = {},
                    onErrorShown = onErrorShown,
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
        seed: SetEntrySeed? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId("exercise-1"),
        name = "Bench Press",
        trackingType = trackingType,
        sets = sets,
        plannedTarget = plannedTarget,
        exerciseId = exerciseId,
        instructions = instructions,
        seed = seed,
    )

    private fun set(
        setNumber: Int,
        load: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        isWarmup: Boolean = false,
        rpe: Double? = null,
        pain: Int? = null,
        technique: Int? = null,
    ) = ActiveSetUi(
        id = WorkoutSetId("set-$setNumber"),
        setNumber = setNumber,
        load = load,
        reps = reps,
        durationSeconds = durationSeconds,
        isWarmup = isWarmup,
        rpe = rpe,
        pain = pain,
        techniqueQuality = technique,
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
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_duration, 1, "0:45"))
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
                composeRule.activity.getString(R.string.workout_active_plan_rest, "1:00"),
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
            composeRule.activity.getString(
                R.string.workout_active_plan_duration_range,
                formatDuration(100_000_000L),
                formatDuration(999_999_999L),
            ),
            composeRule.activity.getString(R.string.workout_active_plan_rest, formatDuration(999_999_999L)),
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
            // Seeded with weight and reps: `Log set` waits for both (CP9, GF-3).
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                seed = SetEntrySeed(load = java.math.BigDecimal("80"), reps = java.math.BigDecimal("8")),
            ),
            onRecordSet = { _, _, _, _, rpe, _, pain, _, _ ->
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
     * Replaces `tappingAddSetClearsTheEntryFields` (remediation-1-remediation-1
     * CP9, B3; its `D60` is superseded): logging a set keeps weight and reps for
     * the next one, and clears RPE, pain, technique and the warm-up flag.
     */
    @Test
    fun tappingLogSetKeepsWeightAndRepsAndClearsTheRest() {
        var recordedLoad: Double? = null
        var recordedReps: Int? = null
        var recordedWarmup = false
        var recordedRpe: Double? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            onRecordSet = { _, load, reps, _, rpe, warmup, _, _, onSaved ->
                recordedLoad = load
                recordedReps = reps
                recordedRpe = rpe
                recordedWarmup = warmup
                onSaved()
            },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_weight))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("2.5").performScrollTo().performClick()
        listOf("6", "0").forEach { key -> composeRule.onNodeWithText(key).performClick() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_confirm)).performClick()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_reps))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("1").performScrollTo().performClick()
        listOf("4", "5").forEach { key -> composeRule.onNodeWithText(key).performClick() }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.repflow_keypad_confirm)).performClick()
        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()
        composeRule.onNode(hasText("8") and isSelectable()).performScrollTo().performClick()
        node(R.string.workout_active_warmup_label).performScrollTo().performClick()
        node(R.string.workout_focus_log_warmup).assertIsDisplayed()
        node(R.string.workout_focus_log_warmup).performClick()

        assertEquals(60.0, recordedLoad)
        assertEquals(45, recordedReps)
        assertEquals(8.0, recordedRpe)
        assertTrue(recordedWarmup)
        // Weight and reps stay...
        composeRule.onNodeWithText("60").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("45").performScrollTo().assertIsDisplayed()
        // ...and the rest clears: no RPE summary, and the warm-up chip is off again.
        node(R.string.workout_active_set_detail_toggle).performScrollTo().assertIsDisplayed()
        node(R.string.workout_focus_log_set).assertIsDisplayed()
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
                composeRule.activity.getString(R.string.progression_result_increase_load) + " — every working set reached 8+ reps",
            ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.progression_result_maintain_load)).assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.progression_result_reduce_load)).assertDoesNotExist()

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.progression_why_content_description, "Back Squat"))
            .performClick()

        assertEquals(squat, opened)
    }

    /** B5: the workout picker's search is `2c`'s 48dp (it had no search test before this). */
    @Test
    fun theWorkoutPickersSearchFieldIsExactly48dpTall() {
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            availableExercises = listOf(benchPickerItem(recommendation = null)),
            openFirstExercise = false,
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_add_exercise)).performScrollTo().performClick()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_picker_search_hint))
            .assertHeightIsEqualTo(48.dp)
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
                composeRule.activity.getString(R.string.progression_result_increase_load) + " — every working set reached 8+ reps",
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
     * A logged set's row opens the correction sheet (`20n`, `D66`): it corrects
     * what was lifted through `onEditSet`, and what isn't touched goes back
     * unchanged, a half RPE included.
     */
    @Test
    fun correctingASetKeepsItsOtherFieldsAndReachesOnEditSet() {
        var edited: List<Any?>? = null
        setContent(
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = listOf(set(1, load = 60.0, reps = 8, rpe = 7.5, pain = 1)),
            ),
            onEditSet = { _, _, load, reps, duration, rpe, isWarmup, pain, technique ->
                edited = listOf(load, reps, duration, rpe, isWarmup, pain, technique)
            },
        )

        composeRule
            .onNode(hasClickAction() and hasText(composeRule.activity.getString(R.string.workout_active_set_row_weight_reps, 1, 60.0, 8)))
            .performScrollTo()
            .performClick()
        node(R.string.workout_focus_correct_title).assertIsDisplayed()
        composeRule
            .onAllNodesWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_reps))
            .onLast()
            .performClick()
        node(R.string.workout_focus_correct_save).performScrollTo().performClick()

        assertEquals(listOf(60.0, 9, null, 7.5, false, 1, null), edited)
    }

    // ---- Timed sets are typed as m:ss (timed-entry-mmss CP2) ----

    /** Not selectable: the correction sheet's scale cells carry the same digits behind the pad. */
    private fun pressKeys(vararg keys: String) = keys.forEach { key -> composeRule.onNode(hasText(key) and !isSelectable()).performClick() }

    private fun pressSet() = node(R.string.repflow_keypad_confirm).performClick()

    private fun selectedPadValue(text: String) = composeRule.onNode(hasText(text) and isSelected())

    @Test
    fun theTimePadTakesMinutesAndSecondsAndTheSetLogsWholeSeconds() {
        var recorded: Int? = null
        setContent(
            exercise(ExerciseTrackingType.DURATION, seed = SetEntrySeed(seconds = java.math.BigDecimal(45))),
            onRecordSet = { _, _, _, duration, _, _, _, _, onSaved ->
                recorded = duration
                onSaved()
            },
        )

        composeRule.onNodeWithText("0:45").performScrollTo().performClick()
        selectedPadValue("0:45").assertIsDisplayed()
        node(R.string.repflow_keypad_duration_caption).assertIsDisplayed()
        pressKeys("1", "3", "0")
        pressSet()
        composeRule.onNodeWithText("1:30").performScrollTo().assertIsDisplayed()
        node(R.string.workout_focus_log_set).performClick()

        assertEquals(90, recorded)
    }

    @Test
    fun aScreenReaderIsGivenTheTimeStepperAndItsPadInWords() {
        setContent(exercise(ExerciseTrackingType.DURATION, seed = SetEntrySeed(seconds = java.math.BigDecimal(120))))

        composeRule.onNode(hasText("2:00") and hasClickAction() and hasContentDescription("2 minutes")).performScrollTo().performClick()
        composeRule.onNode(hasText("2:00") and isSelected() and hasContentDescription("2 minutes")).assertIsDisplayed()
    }

    @Test
    fun theTimeStepperStepsOnTheClock() {
        setContent(exercise(ExerciseTrackingType.DURATION, seed = SetEntrySeed(seconds = java.math.BigDecimal(90))))

        composeRule.onNodeWithText("1:30").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_seconds))
            .performClick()

        composeRule.onNodeWithText("1:35").assertIsDisplayed()
    }

    @Test
    fun theTimeCardSaysItsUnitAndItsHint() {
        setContent(exercise(ExerciseTrackingType.DURATION))

        node(R.string.workout_active_duration_label).assertIsDisplayed()
        assertEquals("Time (m:ss)", composeRule.activity.getString(R.string.workout_active_duration_label))
        composeRule
            .onNodeWithText("Steps of 0:05. Tap the value to type m:ss.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theTimeCardsHintSitsBelowItsTargetLine() {
        setContent(
            exercise(
                ExerciseTrackingType.DURATION,
                plannedTarget =
                    PlannedTargetUi(
                        targetWarmupSets = null,
                        targetWorkingSets = 3,
                        durationRangeSeconds = 30L..60L,
                        restSeconds = 60,
                    ),
            ),
        )

        val hint =
            composeRule
                .onNodeWithText("Steps of 0:05. Tap the value to type m:ss.")
                .performScrollTo()
                .getUnclippedBoundsInRoot()
        val target = composeRule.onNode(hasText("target ", substring = true)).getUnclippedBoundsInRoot()

        assertTrue("the hint is under the target line", hint.top >= target.bottom)
    }

    // ---- Timed values read as m:ss (timed-entry-mmss CP3) ----

    @Test
    fun theTimeCardsTargetReadsAsClocks() {
        setContent(
            exercise(
                ExerciseTrackingType.DURATION,
                plannedTarget = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 3, durationRangeSeconds = 45L..90L),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_focus_seconds_target, "0:45", "1:30"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun setRowsReadAsClocksUnderAndOverAMinute() {
        setContent(exercise(ExerciseTrackingType.DURATION, sets = listOf(set(1, durationSeconds = 45), set(2, durationSeconds = 90))))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_duration, 1, "0:45"))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_duration, 2, "1:30"))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun assertLastLineReads(
        seconds: Int,
        clock: String,
    ) {
        setContent(exercise(ExerciseTrackingType.DURATION, sets = listOf(set(1, durationSeconds = seconds))))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_focus_last_duration, clock), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theLastLineReadsAsAClockUnderAMinute() = assertLastLineReads(45, "0:45")

    @Test
    fun theLastLineReadsAsAClockOverAMinute() = assertLastLineReads(90, "1:30")

    private fun assertLastTimeLineReads(
        seconds: Int,
        clock: String,
    ) {
        setContent(
            exercise(ExerciseTrackingType.DURATION).copy(
                lastPerformance =
                    LastPerformance(load = null, reps = null, durationSeconds = seconds, date = Instant.parse("2026-01-01T00:00:00Z")),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_focus_last_time_duration, clock), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theLastTimeLineReadsAsAClockUnderAMinute() = assertLastTimeLineReads(45, "0:45")

    @Test
    fun theLastTimeLineReadsAsAClockOverAMinute() = assertLastTimeLineReads(90, "1:30")

    private fun assertPendingRowReads(
        range: LongRange,
        rest: Int,
        rangeClocks: Pair<String, String>,
        restClock: String,
    ) {
        setContent(
            exercise(
                ExerciseTrackingType.DURATION,
                plannedTarget =
                    PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 1, durationRangeSeconds = range, restSeconds = rest),
            ),
        )

        val row =
            listOf(
                composeRule.activity.getString(R.string.workout_focus_set_row_pending, 1),
                composeRule.activity.getString(R.string.workout_active_plan_duration_range, rangeClocks.first, rangeClocks.second),
                composeRule.activity.getString(R.string.workout_active_plan_rest, restClock),
            ).joinToString(separator = " · ")
        composeRule.onNodeWithText(row).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun thePendingRowReadsAsClocksUnderAMinute() = assertPendingRowReads(30L..45L, 45, "0:30" to "0:45", "0:45")

    @Test
    fun thePendingRowReadsAsClocksOverAMinute() = assertPendingRowReads(90L..900L, 90, "1:30" to "15:00", "1:30")

    @Test
    fun aWeightPadOpensOnItsValueAndTheFirstKeyReplacesIt() {
        setContent(
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                seed = SetEntrySeed(load = java.math.BigDecimal("82.5"), reps = java.math.BigDecimal("8")),
            ),
        )

        composeRule.onNodeWithText("82.5").performScrollTo().performClick()
        selectedPadValue("82.5").assertIsDisplayed()
        pressKeys("4")
        pressSet()

        composeRule.onNodeWithText("4").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("82.5").assertDoesNotExist()
    }

    private fun correctATimedSetOf45Seconds(onEdited: (Int?) -> Unit) {
        setContent(
            exercise(ExerciseTrackingType.DURATION, sets = listOf(set(1, durationSeconds = 45))),
            onEditSet = { _, _, _, _, duration, _, _, _, _ -> onEdited(duration) },
        )
        composeRule
            .onNode(hasClickAction() and hasText(composeRule.activity.getString(R.string.workout_active_set_row_duration, 1, "0:45")))
            .performScrollTo()
            .performClick()
        node(R.string.workout_focus_correct_title).assertIsDisplayed()
        composeRule.onNodeWithText("0:45").performClick()
        selectedPadValue("0:45").assertIsDisplayed()
    }

    @Test
    fun correctingATimedSetTakesMinutesAndSeconds() {
        var edited: Int? = null
        correctATimedSetOf45Seconds { edited = it }

        pressKeys("1", "3", "0")
        pressSet()
        composeRule.onNodeWithText("1:30").assertIsDisplayed()
        node(R.string.workout_focus_correct_save).performScrollTo().performClick()

        assertEquals(90, edited)
    }

    @Test
    fun correctingATimedSetWithSetUnchangedKeepsItsSeconds() {
        var edited: Int? = null
        correctATimedSetOf45Seconds { edited = it }

        pressSet()
        node(R.string.workout_focus_correct_save).performScrollTo().performClick()

        assertEquals(45, edited)
    }

    // ---- The sheet also edits RPE, pain and technique (set-edit-fields CP1) ----

    private fun openCorrectionSheet(setNumber: Int = 1) {
        composeRule.onNode(hasClickAction() and hasText("Set $setNumber:", substring = true)).performScrollTo().performClick()
        node(R.string.workout_focus_correct_title).assertIsDisplayed()
    }

    /** Opens the sheet on [lastSet]; [onEdited] gets `load, reps, seconds, rpe, isWarmup, pain, technique`. */
    private fun correct(
        lastSet: ActiveSetUi,
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        extraSetFields: ExtraSetFields = ExtraSetFields.COLLAPSED,
        onEdited: (List<Any?>) -> Unit = {},
    ) {
        setContent(
            exercise(trackingType, sets = listOf(lastSet)),
            extraSetFields = extraSetFields,
            onEditSet = { _, _, load, reps, duration, rpe, isWarmup, pain, technique ->
                onEdited(listOf(load, reps, duration, rpe, isWarmup, pain, technique))
            },
        )
        openCorrectionSheet()
    }

    /** Only RPE goes past 5, and 0 to 5 show in RPE, pain and technique, in that order. */
    private fun rpeCell(value: Int) = composeRule.onAllNodes(hasText("$value") and isSelectable())[0]

    private fun painCell(value: Int) = composeRule.onAllNodes(hasText("$value") and isSelectable())[1]

    private fun techniqueCell(value: Int) = composeRule.onAllNodes(hasText("$value") and isSelectable())[2]

    private fun selectedCells() = composeRule.onAllNodes(isSelectable() and isSelected())

    private fun pressSave() = node(R.string.workout_focus_correct_save).performScrollTo().performClick()

    private fun assertTheRowsAreOpenAndFilled(
        extraSetFields: ExtraSetFields,
        disclosuresOnTheScreenBehind: Int,
    ) {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 8.0, pain = 1, technique = 4), extraSetFields = extraSetFields) { edited = it }

        node(R.string.workout_active_rpe_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_pain_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_technique_quality_label).performScrollTo().assertIsDisplayed()
        rpeCell(8).assertIsSelected()
        painCell(1).assertIsSelected()
        techniqueCell(4).assertIsSelected()
        selectedCells().assertCountEquals(3)
        composeRule.onAllNodes(hasStateDescription(R.string.workout_active_set_detail_expanded)).assertCountEquals(0)
        composeRule
            .onAllNodes(hasStateDescription(R.string.workout_active_set_detail_collapsed))
            .assertCountEquals(disclosuresOnTheScreenBehind)
        pressSave()

        assertEquals(listOf(60.0, 8, null, 8.0, false, 1, 4), edited)
    }

    /** One disclosure stays on the logging screen behind the sheet, and the sheet adds none. */
    @Test
    fun theSheetsRowsAreOpenAndFilledWithExtraSetFieldsCollapsed() = assertTheRowsAreOpenAndFilled(ExtraSetFields.COLLAPSED, 1)

    @Test
    fun theSheetsRowsAreOpenAndFilledWithExtraSetFieldsOff() = assertTheRowsAreOpenAndFilled(ExtraSetFields.OFF, 0)

    @Test
    fun aSetWithNoneOfTheThreeSelectsNoCellAndSavesThemEmpty() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8)) { edited = it }

        node(R.string.workout_active_rpe_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_pain_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_technique_quality_label).performScrollTo().assertIsDisplayed()
        selectedCells().assertCountEquals(0)
        pressSave()

        assertEquals(listOf(60.0, 8, null, null, false, null, null), edited)
    }

    @Test
    fun aTimedSetShowsTheSameRowsBelowItsTimeCard() {
        var edited: List<Any?>? = null
        correct(set(1, durationSeconds = 45), trackingType = ExerciseTrackingType.DURATION) { edited = it }

        val timeCard = composeRule.onAllNodesWithText(string(R.string.workout_active_duration_label)).onLast().getUnclippedBoundsInRoot()
        val rpeRow = node(R.string.workout_active_rpe_label).getUnclippedBoundsInRoot()
        assertTrue("the rows are under the time card", rpeRow.top >= timeCard.bottom)
        node(R.string.workout_active_pain_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_technique_quality_label).performScrollTo().assertIsDisplayed()
        selectedCells().assertCountEquals(0)
        pressSave()

        assertEquals(listOf(null, null, 45, null, false, null, null), edited)
    }

    /** The literals, because the wording is the requirement (R1). */
    @Test
    fun theSheetSaysWhichSetItCorrects() {
        correct(set(1, load = 60.0, reps = 8))

        composeRule.onNodeWithText("Correct this set").assertIsDisplayed()
        composeRule.onNodeWithText("Set 1 · Bench Press").assertIsDisplayed()
    }

    @Test
    fun onAWarmUpTheSheetSaysSoAndKeepsTheFlag() {
        var edited: List<Any?>? = null
        correct(set(1, load = 40.0, reps = 10, isWarmup = true)) { edited = it }

        composeRule.onNodeWithText("Correct this set").assertIsDisplayed()
        composeRule.onNodeWithText("Warm-up set · Bench Press").assertIsDisplayed()
        composeRule.onNodeWithText("Set 1 · Bench Press").assertDoesNotExist()
        pressSave()

        assertEquals(listOf(40.0, 10, null, null, true, null, null), edited)
    }

    @Test
    fun tappedCellsReachOnEditSet() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 7.0, pain = 1, technique = 3)) { edited = it }

        rpeCell(9).performScrollTo().performClick()
        painCell(2).performScrollTo().performClick()
        techniqueCell(4).performScrollTo().performClick()
        pressSave()

        assertEquals(listOf(60.0, 8, null, 9.0, false, 2, 4), edited)
    }

    @Test
    fun tappingTheChosenPainCellClearsItAndLeavesTheOtherTwo() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 8.0, pain = 1, technique = 4)) { edited = it }

        painCell(1).performScrollTo().performClick()
        selectedCells().assertCountEquals(2)
        pressSave()

        assertEquals(listOf(60.0, 8, null, 8.0, false, null, 4), edited)
    }

    @Test
    fun tappingTheChosenRpeAndTechniqueCellsClearsThemToo() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 8.0, pain = 1, technique = 4)) { edited = it }

        rpeCell(8).performScrollTo().performClick()
        techniqueCell(4).performScrollTo().performClick()
        selectedCells().assertCountEquals(1)
        pressSave()

        assertEquals(listOf(60.0, 8, null, null, false, 1, null), edited)
    }

    @Test
    fun aHalfRpeSelectsNoCellAndIsSavedAsItWasWhenOnlyTheWeightChanges() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 7.5)) { edited = it }

        selectedCells().assertCountEquals(0)
        composeRule
            .onAllNodesWithContentDescription(composeRule.activity.getString(R.string.workout_focus_more_weight))
            .onLast()
            .performClick()
        pressSave()

        assertEquals(listOf(62.5, 8, null, 7.5, false, null, null), edited)
    }

    @Test
    fun tappingACellOnAHalfRpeReplacesItWithThatNumber() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 7.5)) { edited = it }

        rpeCell(8).performScrollTo().performClick()
        rpeCell(8).assertIsSelected()
        pressSave()

        assertEquals(listOf(60.0, 8, null, 8.0, false, null, null), edited)
    }

    @Test
    fun cancelSavesNothingAndReopeningShowsTheSetsOwnValues() {
        var edited: List<Any?>? = null
        correct(set(1, load = 60.0, reps = 8, rpe = 8.0, pain = 1, technique = 4)) { edited = it }

        rpeCell(9).performScrollTo().performClick()
        painCell(2).performScrollTo().performClick()
        techniqueCell(3).performScrollTo().performClick()
        node(R.string.workout_focus_correct_cancel).performScrollTo().performClick()

        node(R.string.workout_focus_correct_title).assertDoesNotExist()
        assertEquals(null, edited)

        openCorrectionSheet()
        rpeCell(8).assertIsSelected()
        painCell(1).assertIsSelected()
        techniqueCell(4).assertIsSelected()
        selectedCells().assertCountEquals(3)
    }

    @Test
    fun theSheetShowsItsOwnHintAndNotTheLoggingOne() {
        correct(set(1, load = 60.0, reps = 8))

        composeRule.onNodeWithText("Optional. Tap a chosen number again to clear it.").performScrollTo().assertIsDisplayed()
        node(R.string.workout_focus_detail_hint).assertDoesNotExist()
    }

    @Test
    fun theLoggingRowsKeepTheirOwnHintAndNotTheSheets() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS))

        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()

        node(R.string.workout_focus_detail_hint).performScrollTo().assertIsDisplayed()
        node(R.string.workout_focus_correct_hint).assertDoesNotExist()
    }

    /** A box shorter than the content, so `Save` is out of reach unless the content scrolls. */
    private fun setCorrectionContentAtFontScale(
        fontScale: Float,
        onSaved: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                    Box(modifier = Modifier.height(SHORT_SHEET_HEIGHT.dp)) {
                        SetCorrectionContent(
                            exercise = exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
                            set = set(1, load = 60.0, reps = 8),
                            onCancel = {},
                            onSave = { _, _, _, _, _, _ -> onSaved() },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun atLargeFontSaveCanBeScrolledToAndPressedWithCancelAboveIt() {
        var saved = 0
        setCorrectionContentAtFontScale(LARGE_FONT_SCALE_MAX) { saved++ }

        val save = node(R.string.workout_focus_correct_save).performScrollTo().assertIsDisplayed()
        val cancel = node(R.string.workout_focus_correct_cancel).assertIsDisplayed()
        val saveBounds = save.getUnclippedBoundsInRoot()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        assertTrue("$cancelBounds is above $saveBounds", cancelBounds.bottom <= saveBounds.top)
        assertEquals(cancelBounds.left, saveBounds.left)
        save.performClick()

        composeRule.runOnIdle { assertEquals(1, saved) }
    }

    @Test
    fun atNormalFontCancelAndSaveSitSideBySideEach48DpTall() {
        setCorrectionContentAtFontScale(1f)

        val save = node(R.string.workout_focus_correct_save).performScrollTo().assertIsDisplayed()
        val cancel = node(R.string.workout_focus_correct_cancel).assertIsDisplayed()
        val saveBounds = save.getUnclippedBoundsInRoot()
        val cancelBounds = cancel.getUnclippedBoundsInRoot()
        assertTrue("$cancelBounds is left of $saveBounds", cancelBounds.right <= saveBounds.left)
        assertEquals(cancelBounds.top, saveBounds.top)
        save.assertHeightIsEqualTo(48.dp)
        cancel.assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun tappedValuesSurviveARestoreAndAreSaved() {
        var saved: List<Any?>? = null
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            RepFlowTheme {
                SetCorrectionContent(
                    exercise = exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
                    set = set(1, load = 60.0, reps = 8, rpe = 6.0, pain = 1, technique = 2),
                    onCancel = {},
                    onSave = { _, _, _, rpe, pain, technique -> saved = listOf(rpe, pain, technique) },
                )
            }
        }

        rpeCell(9).performScrollTo().performClick()
        painCell(3).performScrollTo().performClick()
        techniqueCell(4).performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()

        rpeCell(9).assertIsSelected()
        painCell(3).assertIsSelected()
        techniqueCell(4).assertIsSelected()
        pressSave()

        assertEquals(listOf(9.0, 3, 4), saved)
    }

    // ---- Any logged set can be corrected (set-edit-any-set CP2) ----

    /** The reps are above 10 so a stepper's value can't be taken for a scale cell. */
    private fun firstSet() = set(1, load = 60.0, reps = 12, rpe = 6.0, pain = 1, technique = 2)

    private fun secondSet() = set(2, load = 62.5, reps = 11, rpe = 9.0, pain = 3, technique = 4)

    private fun thirdSet() = set(3, load = 65.0, reps = 13, rpe = 7.0, pain = 0, technique = 5)

    private fun threeSets(plannedTarget: PlannedTargetUi? = null) =
        exercise(
            ExerciseTrackingType.WEIGHT_AND_REPS,
            sets = listOf(firstSet(), secondSet(), thirdSet()),
            plannedTarget = plannedTarget,
        )

    /** Gives [onEdited] `set id, load, reps, seconds, rpe, isWarmup, pain, technique`. */
    private fun recordingEdits(
        onEdited: (List<Any?>) -> Unit,
    ): (WorkoutExerciseId, WorkoutSetId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit =
        { _, setId, load, reps, seconds, rpe, isWarmup, pain, technique ->
            onEdited(listOf(setId.value, load, reps, seconds, rpe, isWarmup, pain, technique))
        }

    /**
     * Focus mode alone, as the activity's own content, for what changes the exercise under it or restores it.
     * [exercise] is read in the composition.
     *
     * A restore here recreates the activity, as a rotation does. `StateRestorationTester` can't stand in for
     * it: the sheet's window keeps its values in a registry of its own, saved only when the activity is, so
     * under the tester they are simply lost.
     */
    private fun setFocus(
        exercise: () -> ActiveExerciseUi,
        onEdited: (List<Any?>) -> Unit = {},
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                RepFlowTheme {
                    val current = exercise()
                    WorkoutFocus(
                        content =
                            ActiveWorkoutContent.Active(
                                sessionId = WorkoutSessionId("session-1"),
                                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                                exercises = listOf(current),
                            ),
                        exercise = current,
                        entry = rememberSaveable(saver = SetEntryState.Saver) { SetEntryState().also { it.applySeed(current.seed) } },
                        recommendation = null,
                        onBackToBoard = {},
                        onFinishClick = {},
                        onNextExercise = {},
                        onOpenRecommendation = {},
                        onRecordSet = { _, _, _, _, _, _, _, _, _ -> },
                        onUndoLastSet = {},
                        onEditSet = recordingEdits(onEdited),
                        restStrip = {},
                        snackbarHostState = remember { SnackbarHostState() },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Saves the state as it stands, then mounts focus mode again on [exercise] in the new activity. */
    private fun recreateOnto(
        exercise: () -> ActiveExerciseUi,
        onEdited: (List<Any?>) -> Unit = {},
    ) {
        composeRule.activityRule.scenario.recreate()
        setFocus(exercise, onEdited)
    }

    private fun assertTheSheetIsOn(
        subtitle: String,
        load: String,
        reps: String,
        rpe: Int,
        pain: Int,
        technique: Int,
    ) {
        composeRule.onNodeWithText(subtitle).assertIsDisplayed()
        composeRule.onNodeWithText(load).assertIsDisplayed()
        composeRule.onNodeWithText(reps).assertIsDisplayed()
        rpeCell(rpe).assertIsSelected()
        painCell(pain).assertIsSelected()
        techniqueCell(technique).assertIsSelected()
        selectedCells().assertCountEquals(3)
    }

    private fun hasTheCorrectAction() =
        SemanticsMatcher("has the click label `Correct this set`") { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.label == string(R.string.workout_focus_correct_action)
        }

    private fun pressMoreWeightOnTheSheet() =
        composeRule.onAllNodesWithContentDescription(string(R.string.workout_focus_more_weight)).onLast().performClick()

    private fun pressLessWeightOnTheSheet() =
        composeRule.onAllNodesWithContentDescription(string(R.string.workout_focus_less_weight)).onLast().performClick()

    @Test
    fun everyLoggedSetHasThePencilAndTheCorrectActionAndAPendingRowHasNeither() {
        setContent(threeSets(plannedTarget = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 4)))

        composeRule.onAllNodesWithTag(SET_ROW_PENCIL_TAG, useUnmergedTree = true).assertCountEquals(3)
        composeRule.onAllNodesWithTag(SET_ROW_PENCIL_TAG, useUnmergedTree = true)[0].assertWidthIsEqualTo(20.dp)
        (1..3).forEach { setNumber ->
            composeRule.onNode(hasText("Set $setNumber:", substring = true)).assert(hasTheCorrectAction())
        }
        composeRule.onNode(hasText("Set 4: not logged", substring = true)).assert(!hasClickAction())
    }

    @Test
    fun anEarlierRowOpensTheSheetOnItsOwnValuesAndSaveNamesThatSet() {
        var edited: List<Any?>? = null
        setContent(threeSets(), onEditSet = recordingEdits { edited = it })

        openCorrectionSheet(1)
        assertTheSheetIsOn("Set 1 · Bench Press", load = "60", reps = "12", rpe = 6, pain = 1, technique = 2)
        pressMoreWeightOnTheSheet()
        pressSave()

        assertEquals(listOf("set-1", 62.5, 12, null, 6.0, false, 1, 2), edited)
    }

    @Test
    fun aCancelledSheetLeavesNothingOnTheNextSetsSheet() {
        setContent(threeSets())

        openCorrectionSheet(2)
        assertTheSheetIsOn("Set 2 · Bench Press", load = "62.5", reps = "11", rpe = 9, pain = 3, technique = 4)
        rpeCell(1).performScrollTo().performClick()
        node(R.string.workout_focus_correct_cancel).performScrollTo().performClick()
        node(R.string.workout_focus_correct_title).assertDoesNotExist()

        openCorrectionSheet(3)

        assertTheSheetIsOn("Set 3 · Bench Press", load = "65", reps = "13", rpe = 7, pain = 0, technique = 5)
    }

    @Test
    fun aWarmUpThatIsNotTheLastSetSaysSoAndKeepsItsFlagAndItsId() {
        var edited: List<Any?>? = null
        setContent(
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = listOf(set(1, load = 40.0, reps = 12, isWarmup = true), set(2, load = 60.0, reps = 11)),
            ),
            onEditSet = recordingEdits { edited = it },
        )

        openCorrectionSheet(1)
        composeRule.onNodeWithText("Warm-up set · Bench Press").assertIsDisplayed()
        pressSave()

        assertEquals(listOf("set-1", 40.0, 12, null, null, true, null, null), edited)
    }

    @Test
    fun aRotationBringsTheSheetBackOnTheSameSetWithATappedValue() {
        var edited: List<Any?>? = null
        setFocus({ threeSets() })
        openCorrectionSheet(1)
        rpeCell(3).performScrollTo().performClick()

        recreateOnto({ threeSets() }, onEdited = { edited = it })

        assertTheSheetIsOn("Set 1 · Bench Press", load = "60", reps = "12", rpe = 3, pain = 1, technique = 2)
        pressSave()

        assertEquals(listOf("set-1", 60.0, 12, null, 3.0, false, 1, 2), edited)
    }

    @Test
    fun theSheetClosesWhenItsSetGoesAndDoesNotComeBackWithIt() {
        val current = mutableStateOf(threeSets())
        setFocus({ current.value })
        openCorrectionSheet(1)

        composeRule.runOnIdle { current.value = threeSets().copy(sets = listOf(secondSet(), thirdSet())) }
        composeRule.waitForIdle()
        node(R.string.workout_focus_correct_title).assertDoesNotExist()

        composeRule.runOnIdle { current.value = threeSets() }
        composeRule.waitForIdle()
        node(R.string.workout_focus_correct_title).assertDoesNotExist()

        openCorrectionSheet(2)
        assertTheSheetIsOn("Set 2 · Bench Press", load = "62.5", reps = "11", rpe = 9, pain = 3, technique = 4)
    }

    @Test
    fun undoLastStillReportsOnceAndTheLastLineNamesTheMostRecentSet() {
        var undone = 0
        var edited: List<Any?>? = null
        val lastLine = composeRule.activity.getString(R.string.workout_focus_last_weight_reps, "65", 13)
        setContent(threeSets(), onUndoLastSet = { undone++ }, onEditSet = recordingEdits { edited = it })
        composeRule.onNodeWithText(lastLine).performScrollTo().assertIsDisplayed()

        node(R.string.workout_focus_undo_last).performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(1, undone) }
        assertEquals(null, edited)
        node(R.string.workout_focus_correct_title).assertDoesNotExist()
        composeRule.onNodeWithText(lastLine).assertIsDisplayed()
    }

    /** The row moves and the steppers don't, through the seed the correction changes, a restore, and an undo. */
    @Test
    fun aSavedCorrectionHoldsTheSteppers() {
        val logged =
            exercise(
                ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = listOf(set(1, load = 82.5, reps = 8)),
                seed = SetEntrySeed(load = BigDecimal("82.5"), reps = BigDecimal("8")),
            )
        val current = mutableStateOf(logged)
        var edited: List<Any?>? = null
        setFocus({ current.value }, onEdited = { edited = it })
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()

        openCorrectionSheet(1)
        pressLessWeightOnTheSheet()
        pressSave()
        assertEquals(listOf("set-1", 80.0, 8, null, null, false, null, null), edited)
        composeRule.runOnIdle {
            current.value =
                logged.copy(
                    sets = listOf(set(1, load = 80.0, reps = 8)),
                    seed = SetEntrySeed(load = BigDecimal("80"), reps = BigDecimal("8")),
                )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_set_row_weight_reps, 1, 80.0, 8)).assertExists()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("80").assertDoesNotExist()

        recreateOnto({ current.value })

        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("80").assertDoesNotExist()

        composeRule.runOnIdle {
            current.value = logged.copy(sets = emptyList(), seed = SetEntrySeed(load = BigDecimal("75"), reps = BigDecimal("8")))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("75").assertDoesNotExist()
    }

    /** A sheet saved on a set that is gone by the restore must not hand its values to the next sheet. */
    @Test
    fun restoredValuesDoNotCrossToAnotherSet() {
        var edited: List<Any?>? = null
        setFocus({ threeSets() })
        openCorrectionSheet(1)
        rpeCell(3).performScrollTo().performClick()

        recreateOnto({ threeSets().copy(sets = listOf(secondSet(), thirdSet())) }, onEdited = { edited = it })

        node(R.string.workout_focus_correct_title).assertDoesNotExist()
        openCorrectionSheet(2)
        assertTheSheetIsOn("Set 2 · Bench Press", load = "62.5", reps = "11", rpe = 9, pain = 3, technique = 4)
        pressSave()

        assertEquals(listOf("set-2", 62.5, 11, null, 9.0, false, 3, 4), edited)
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

    /** `Skip rest` must read in full on a 384dp phone, not wrap to "Skip" (functional review A2). */
    @Test
    fun theRestStripsSkipLabelFitsOnOneLineAt384dp() {
        composeRule.setContent {
            RepFlowTheme {
                Box(Modifier.width(384.dp)) {
                    RestTimerBar(
                        timer = RestTimerUi(endAt = Instant.now().plusSeconds(90), totalDurationSeconds = 90),
                        onAddRestTime = {},
                        onRemoveRestTime = {},
                        onSkipRestTimer = {},
                    )
                }
            }
        }

        val results = mutableListOf<TextLayoutResult>()
        val node = composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_rest_skip))
        node.assertIsDisplayed()
        node
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        assertEquals(1, results.single().lineCount)
    }

    /** At 384dp and font 1.0 the strip's three buttons share one row at one height (the 44dp floor, P2-F-2 / I1). */
    @Test
    fun theRestStripsThreeButtonsShareOneRowAndHeightAtDefaultFont() {
        showRestStrip(fontScale = 1f)
        val bounds =
            REST_STRIP_LABELS.map { label ->
                composeRule.onNode(hasClickAction() and hasText(string(label))).getUnclippedBoundsInRoot()
            }
        assertTrue("buttons do not share one top: $bounds", bounds.all { kotlin.math.abs(it.top.value - bounds.first().top.value) < 0.5f })
        assertTrue(
            "buttons differ in height: $bounds",
            bounds.all { kotlin.math.abs(it.height.value - bounds.first().height.value) < 0.5f },
        )
        // Pixel rounding makes 44dp read 44.09dp, so compare with a half-dp tolerance.
        assertTrue("buttons are not the 44dp floor: $bounds", kotlin.math.abs(bounds.first().height.value - 44f) < 0.5f)
    }

    /** At 1.3x the strip's button labels are fully rendered (design 9f, P2-F-2). */
    @Test
    fun theRestStripsButtonLabelsStayWholeAt1point3xFont() = assertRestStripLabelsWhole(LARGE_FONT_SCALE_MEDIUM)

    /** At 2.0x the strip's button labels are fully rendered (design 9f, P2-F-2). */
    @Test
    fun theRestStripsButtonLabelsStayWholeAt2xFont() = assertRestStripLabelsWhole(LARGE_FONT_SCALE_MAX)

    private fun showRestStrip(fontScale: Float) {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                RepFlowTheme {
                    Box(Modifier.width(384.dp)) {
                        RestTimerBar(
                            timer = RestTimerUi(endAt = Instant.now().plusSeconds(90), totalDurationSeconds = 90),
                            onAddRestTime = {},
                            onRemoveRestTime = {},
                            onSkipRestTimer = {},
                        )
                    }
                }
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = composeRule.activity.getString(id)

    private fun assertRestStripLabelsWhole(fontScale: Float) {
        showRestStrip(fontScale)
        composeRule.waitForIdle()
        REST_STRIP_LABELS.forEach { label ->
            composeRule.assertButtonLabelWhole(string(label), "font $fontScale")
        }
    }

    /** The done strip is neutral: check, `Rest done` / `Next set is ready` and the X, with no nudge or skip buttons (P2-F-5). */
    @Test
    fun theDoneRestStripOffersOnlyTheDismissX() {
        var dismissed = 0
        composeRule.setContent {
            RepFlowTheme {
                Box(Modifier.width(384.dp)) {
                    RestTimerBar(
                        timer = RestTimerUi(endAt = Instant.now().minusSeconds(1), totalDurationSeconds = 90),
                        onAddRestTime = {},
                        onRemoveRestTime = {},
                        onSkipRestTimer = { dismissed++ },
                    )
                }
            }
        }

        node(R.string.workout_rest_done).assertIsDisplayed()
        node(R.string.workout_rest_ready).assertIsDisplayed()
        node(R.string.workout_rest_skip).assertDoesNotExist()
        node(R.string.workout_active_rest_timer_add).assertDoesNotExist()
        node(R.string.workout_active_rest_timer_remove).assertDoesNotExist()
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.workout_rest_dismiss_content_description))
            .performClick()
        assertEquals(1, dismissed)
    }

    /** A one-kilo step reads "1 step", not "1 steps" (functional review A3). */
    @Test
    fun theWeightCaptionIsSingularForAOneKiloStep() {
        val base = exercise(ExerciseTrackingType.WEIGHT_AND_REPS)
        setContent(base.copy(defaultLoadIncrement = java.math.BigDecimal.ONE))

        composeRule.onNodeWithText("kg · 1 step").assertIsDisplayed()
    }

    @Test
    fun theWeightCaptionStaysPluralForOtherSteps() {
        val base = exercise(ExerciseTrackingType.WEIGHT_AND_REPS)
        setContent(base.copy(defaultLoadIncrement = java.math.BigDecimal("2.5")))

        composeRule.onNodeWithText("kg · 2.5 steps").assertIsDisplayed()
    }

    /** A failed action shows the generic message and reports it shown, so the ViewModel clears the field (A-1). */
    @Test
    fun aFailedActionShowsTheMessageAndReportsItShown() {
        var shown = 0
        val error = mutableStateOf<ActiveWorkoutErrorReason?>(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE)
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS), errorMessage = error, onErrorShown = { shown++ })

        node(R.string.workout_active_action_failed).assertIsDisplayed()

        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { shown == 1 }
    }

    @Test
    fun noErrorShowsNoMessage() {
        var shown = 0
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS), onErrorShown = { shown++ })

        node(R.string.workout_active_action_failed).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, shown) }
    }

    /** Clearing the field is what lets an identical failure re-trigger the message. */
    @Test
    fun theSameFailureShowsTheMessageAgainAfterItWasCleared() {
        val error = mutableStateOf<ActiveWorkoutErrorReason?>(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE)
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            errorMessage = error,
            onErrorShown = { error.value = null },
        )
        node(R.string.workout_active_action_failed).assertIsDisplayed()
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { error.value == null }
        composeRule.waitUntil(SNACKBAR_WAIT_MILLIS) { actionFailedNodes().isEmpty() }

        composeRule.runOnIdle { error.value = ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE }

        node(R.string.workout_active_action_failed).assertIsDisplayed()
    }

    /** A retry that worked clears the field while the message is still up: it goes then, without waiting out its time. */
    @Test
    fun clearingTheFieldTakesDownTheShowingMessage() {
        var shown = 0
        val error = mutableStateOf<ActiveWorkoutErrorReason?>(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE)
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS), errorMessage = error, onErrorShown = { shown++ })
        node(R.string.workout_active_action_failed).assertIsDisplayed()

        composeRule.runOnIdle { error.value = null }

        node(R.string.workout_active_action_failed).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(0, shown) }
    }

    @Test
    fun inFocusModeTheMessageSitsAboveLogSetWithNoRestStrip() {
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            errorMessage = mutableStateOf(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE),
        )

        assertMessageSitsAbove(R.string.workout_focus_log_set)
    }

    @Test
    fun inFocusModeTheMessageSitsAboveTheRestStripAndLogSet() {
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            errorMessage = mutableStateOf(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE),
            restTimer = runningRest(),
        )

        assertMessageSitsAbove(R.string.workout_focus_log_set)
        assertMessageSitsAbove(R.string.workout_rest_skip)
    }

    @Test
    fun onTheBoardTheMessageSitsAboveTheRestStrip() {
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            openFirstExercise = false,
            errorMessage = mutableStateOf(ActiveWorkoutErrorReason.PERSISTENCE_UNAVAILABLE),
            restTimer = runningRest(),
        )

        assertMessageSitsAbove(R.string.workout_rest_skip)
    }

    /** A-12: the literal, because the wording is the requirement (R4) and a resolved resource would pass on any value. */
    @Test
    fun theLoadFailureStateOffersTryAgainAndTappingItCallsOnRetry() {
        var retries = 0
        composeRule.setContent {
            RepFlowTheme {
                ActiveWorkoutScreen(
                    uiState = ActiveWorkoutUiState(content = ActiveWorkoutContent.ObservationFailed(ActiveWorkoutErrorReason.UNKNOWN)),
                    dayContext = null,
                    focusedExerciseId = null,
                    onFocusExercise = {},
                    onAddExercise = {},
                    onCreateExercise = {},
                    onOpenRecommendation = {},
                    onRecordSet = { _, _, _, _, _, _, _, _, _ -> },
                    onUndoLastSet = {},
                    onEditSet = { _, _, _, _, _, _, _, _, _ -> },
                    onAddRestTime = {},
                    onRemoveRestTime = {},
                    onSkipRestTimer = {},
                    onCompleteWorkout = {},
                    onLeaveWorkout = {},
                    onAbandonWorkout = {},
                    onRetry = { retries++ },
                    onErrorShown = {},
                )
            }
        }

        node(R.string.workout_active_observation_failed).assertIsDisplayed()
        composeRule.onNode(hasClickAction() and hasText("Try again")).performClick()

        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    private fun runningRest() = RestTimerUi(endAt = Instant.now().plusSeconds(90), totalDurationSeconds = 90)

    private fun actionFailedNodes() = composeRule.onAllNodesWithText(string(R.string.workout_active_action_failed)).fetchSemanticsNodes()

    /**
     * Compares the whole snackbar, not only its words: the Material host marks the item it draws with a
     * dismiss action, which nothing else on these surfaces carries while no sheet is open.
     */
    private fun assertMessageSitsAbove(
        @StringRes label: Int,
    ) {
        node(R.string.workout_active_action_failed).assertIsDisplayed()
        val message = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).getUnclippedBoundsInRoot()
        val control = composeRule.onNode(hasClickAction() and hasText(string(label))).getUnclippedBoundsInRoot()
        assertTrue("message $message overlaps ${string(label)} $control", message.bottom <= control.top)
    }
}

private val REST_STRIP_LABELS =
    listOf(
        R.string.workout_active_rest_timer_remove,
        R.string.workout_active_rest_timer_add,
        R.string.workout_rest_skip,
    )

private const val SNACKBAR_WAIT_MILLIS = 10_000L
private const val LARGE_FONT_SCALE_MEDIUM = 1.3f
private const val LARGE_FONT_SCALE_MAX = 2.0f
private const val SHORT_SHEET_HEIGHT = 300
