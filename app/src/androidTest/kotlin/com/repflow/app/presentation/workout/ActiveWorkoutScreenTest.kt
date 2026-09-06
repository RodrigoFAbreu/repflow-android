package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
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

    private fun setContent(
        exercise: ActiveExerciseUi,
        onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit = { _, _, _, _, _, _, _, _ -> },
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
                                    exercises = listOf(exercise),
                                ),
                        ),
                    dayContext = null,
                    onStartWorkout = {},
                    onAddExercise = {},
                    onOverrideRecommendation = { _, _ -> },
                    onRecordSet = onRecordSet,
                    onUndoLastSet = {},
                    onEditLastSet = { _, _, _, _, _, _, _, _ -> },
                    onAddRestTime = {},
                    onRemoveRestTime = {},
                    onSkipRestTimer = {},
                    onCompleteWorkout = {},
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
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId("exercise-1"),
        name = "Bench Press",
        trackingType = trackingType,
        sets = sets,
        plannedTarget = plannedTarget,
    )

    private fun set(
        setNumber: Int,
        load: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        isWarmup: Boolean = false,
    ) = ActiveSetUi(
        id = WorkoutSetId("set-$setNumber"),
        setNumber = setNumber,
        load = load,
        reps = reps,
        durationSeconds = durationSeconds,
        isWarmup = isWarmup,
    )

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

    @Test
    fun aPlannedExerciseShowsWarmupAndWorkingProgress() {
        val target = PlannedTargetUi(targetWarmupSets = 2, targetWorkingSets = 3, repRange = 8..12, restSeconds = 60)
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = target))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_plan_warmup_progress, 0, 2))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_plan_working_progress, 0, 3))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_plan_rep_range, 8, 12))
            .assertIsDisplayed()
    }

    @Test
    fun everyPlannedTargetChipStaysOnScreenWhenTheRowOutgrowsTheWidth() {
        // The guard this pins is `weight(1f, fill = false)` on each chip plus
        // `RepFlowStatusChip`'s own `maxLines = 1`/`Ellipsis`: a row that
        // outgrows the screen must degrade visibly rather than push its last
        // chip off the right edge. Without the weight the third chip measures
        // to zero width and stops being displayed - which is exactly the
        // silent clipping the round-1 fix exists to prevent, and which was
        // otherwise verified only by eye. Values are deliberately absurd so
        // the row cannot fit at any phone width.
        val target =
            PlannedTargetUi(
                targetWarmupSets = null,
                targetWorkingSets = 1,
                repRange = 100_000_000..999_999_999,
                durationRangeSeconds = 100_000_000L..999_999_999L,
                restSeconds = 999_999_999,
            )
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = target))

        listOf(
            composeRule.activity.getString(R.string.workout_active_plan_rep_range, 100_000_000, 999_999_999),
            composeRule.activity.getString(R.string.workout_active_plan_duration_range, 100_000_000L, 999_999_999L),
            composeRule.activity.getString(R.string.workout_active_plan_rest, 999_999_999),
        ).forEach { chipText ->
            composeRule.onNodeWithText(chipText).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun anAdHocExerciseShowsNoPlannedTargetSummary() {
        setContent(exercise(ExerciseTrackingType.WEIGHT_AND_REPS, plannedTarget = null))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.workout_active_plan_working_progress, 0, 0))
            .assertDoesNotExist()
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
     * The set-detail disclosure is the one new user-facing interaction CP6
     * introduces, and it gates three data-entry fields that had no coverage
     * before it existed. The four tests below pin the whole contract: what
     * the disclosure hides, that opening it reveals all three fields, that a
     * value typed into them reaches `onRecordSet` whether the section is open
     * or closed at submit time (the retention property `SetDetailSection`'s
     * KDoc claims), and that the header node a screen reader activates
     * actually reads differently in the two states.
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
        node(R.string.workout_active_rpe_label).performScrollTo().performTextInput("8")
        node(R.string.workout_active_pain_label).performScrollTo().performTextInput("2")
        // Collapse again before submitting: the values live in ExerciseCard's
        // own state, not in the section, so hiding them must not drop them.
        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()
        node(R.string.workout_active_add_set).performScrollTo().performClick()

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

    @Test
    fun tappingAddSetClearsTheEntryFields() {
        var recordedLoad: Double? = null
        setContent(
            exercise(ExerciseTrackingType.WEIGHT_AND_REPS),
            onRecordSet = { _, load, _, _, _, _, _, _ -> recordedLoad = load },
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_load_label)).performTextInput("60")
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.workout_active_add_set)).performClick()

        assertEquals(60.0, recordedLoad)
        composeRule.onNodeWithText("60").assertDoesNotExist()
    }
}
