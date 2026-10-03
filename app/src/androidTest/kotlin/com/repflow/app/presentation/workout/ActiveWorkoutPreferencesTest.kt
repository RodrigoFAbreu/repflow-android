package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.settings.ExtraSetFields
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * remediation-1-remediation-1 CP6: focus mode applies the new preferences -
 * `Extra set fields` in its three modes, and the rest hint's precedence (the
 * plan row's rest, else the exercise's own `Default rest`, else the app's).
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutPreferencesTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Read inside the composition, so a test can switch the mode the way Settings does while focus mode keeps its entry. */
    private var extraSetFieldsMode by mutableStateOf(ExtraSetFields.COLLAPSED)

    private fun setContent(
        extraSetFields: ExtraSetFields = ExtraSetFields.COLLAPSED,
        appDefaultRestSeconds: Int = 90,
        exerciseDefaultRestSeconds: Int? = null,
        plannedRestSeconds: Int? = null,
        onRecordExtras: (rpe: Double?, pain: Int?, technique: Int?) -> Unit = { _, _, _ -> },
    ) {
        extraSetFieldsMode = extraSetFields
        val exercise =
            ActiveExerciseUi(
                id = WorkoutExerciseId("exercise-1"),
                name = "Bench Press",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = emptyList(),
                plannedTarget =
                    plannedRestSeconds?.let {
                        PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 3, repRange = 8..12, restSeconds = it)
                    },
                defaultRestSeconds = exerciseDefaultRestSeconds,
                // Seeded with reps, so `Log set` is enabled (CP9).
                seed = SetEntrySeed(load = java.math.BigDecimal("80"), reps = java.math.BigDecimal("8")),
            )
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
                                    appDefaultRestSeconds = appDefaultRestSeconds,
                                    extraSetFields = extraSetFieldsMode,
                                ),
                        ),
                    dayContext = null,
                    focusedExerciseId = exercise.id,
                    onFocusExercise = {},
                    onAddExercise = {},
                    onCreateExercise = {},
                    onOpenRecommendation = {},
                    onRecordSet = { _, _, _, _, rpe, _, pain, technique -> onRecordExtras(rpe, pain, technique) },
                    onUndoLastSet = {},
                    onEditLastSet = { _, _, _, _, _, _, _, _ -> },
                    onAddRestTime = {},
                    onRemoveRestTime = {},
                    onSkipRestTimer = {},
                    onCompleteWorkout = {},
                    onLeaveWorkout = {},
                    onAbandonWorkout = {},
                    onRetry = {},
                )
            }
        }
    }

    private fun node(
        @StringRes label: Int,
    ) = composeRule.onNodeWithText(composeRule.activity.getString(label))

    @Test
    fun collapsedKeepsTheDisclosureAndHidesTheRows() {
        setContent(ExtraSetFields.COLLAPSED)

        node(R.string.workout_active_set_detail_toggle).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_rpe_label).assertDoesNotExist()
    }

    @Test
    fun alwaysShownDrawsTheRowsWithoutADisclosure() {
        setContent(ExtraSetFields.ALWAYS_SHOWN)

        node(R.string.workout_active_set_detail_toggle).assertDoesNotExist()
        node(R.string.workout_active_rpe_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_pain_label).performScrollTo().assertIsDisplayed()
        node(R.string.workout_active_technique_quality_label).performScrollTo().assertIsDisplayed()
    }

    /**
     * The values are entered while the section is on and `Off` is chosen
     * afterwards (the reachable path: Settings, then back). The entry keeps
     * them, and a hidden section must submit none of them (Q2, CP6 item 3).
     */
    @Test
    fun offDrawsNeitherTheDisclosureNorTheRowsAndSubmitsNoExtraFieldEnteredEarlier() {
        var recorded: Triple<Double?, Int?, Int?>? = null
        setContent(ExtraSetFields.COLLAPSED, onRecordExtras = { rpe, pain, technique -> recorded = Triple(rpe, pain, technique) })

        node(R.string.workout_active_set_detail_toggle).performScrollTo().performClick()
        // RPE's row is the only one with an 8; the three rows each have a 2, in the order RPE, pain, technique.
        composeRule.onNode(hasText("8") and isSelectable()).performScrollTo().performClick()
        composeRule.onAllNodes(hasText("2") and isSelectable())[1].performScrollTo().performClick()
        composeRule.onAllNodes(hasText("2") and isSelectable())[2].performScrollTo().performClick()

        composeRule.runOnIdle { extraSetFieldsMode = ExtraSetFields.OFF }
        composeRule.waitForIdle()

        node(R.string.workout_active_set_detail_toggle).assertDoesNotExist()
        node(R.string.workout_active_rpe_label).assertDoesNotExist()
        node(R.string.workout_active_pain_label).assertDoesNotExist()
        node(R.string.workout_active_technique_quality_label).assertDoesNotExist()
        node(R.string.workout_focus_log_set).performClick()

        assertEquals(Triple<Double?, Int?, Int?>(null, null, null), recorded)
    }

    private fun hint(seconds: Int) = composeRule.activity.getString(R.string.workout_focus_warmup_hint_off, seconds)

    @Test
    fun theWarmupHintShowsTheExercisesOwnDefaultRestInAnAdHocWorkout() {
        setContent(appDefaultRestSeconds = 120, exerciseDefaultRestSeconds = 150)

        composeRule.onNodeWithText(hint(150)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theWarmupHintShowsTheAppDefaultWhenNeitherThePlanNorTheExerciseSetsARest() {
        setContent(appDefaultRestSeconds = 120)

        composeRule.onNodeWithText(hint(120)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theWarmupHintShowsThePlanRowsRestOverBoth() {
        setContent(appDefaultRestSeconds = 120, exerciseDefaultRestSeconds = 150, plannedRestSeconds = 45)

        composeRule.onNodeWithText(hint(45)).performScrollTo().assertIsDisplayed()
    }
}
