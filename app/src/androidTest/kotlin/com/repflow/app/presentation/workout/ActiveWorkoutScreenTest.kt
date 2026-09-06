package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
