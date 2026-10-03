package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.workout.LastPerformance
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSetId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

/**
 * B3 (remediation-1-remediation-1 CP9): set entry keeps weight and reps after a
 * set and seeds from the last session. The harness is stateful - it holds the
 * focus and appends each recorded set, recomputing the seed as
 * `ActiveWorkoutViewModel` does - so reopening an exercise can be exercised
 * (the stateless `ActiveWorkoutScreenTest.setContent` cannot).
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutSetEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var exercises by mutableStateOf<List<ActiveExerciseUi>>(emptyList())
    private var focusedId by mutableStateOf<WorkoutExerciseId?>(null)
    private val recordedLoads = mutableListOf<Double?>()

    private val lastTime = LastPerformance(load = 80.0, reps = 8, durationSeconds = null, date = Instant.parse("2026-05-05T08:00:00Z"))
    private val lastTimeSeed = SetEntrySeed(load = BigDecimal("80"), reps = BigDecimal("8"))

    private fun exercise(
        id: String = "e1",
        name: String = "Bench Press",
        last: LastPerformance? = null,
        seed: SetEntrySeed? = null,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId(id),
        name = name,
        trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        sets = emptyList(),
        plannedTarget = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 3),
        lastPerformance = last,
        seed = seed,
    )

    private fun start(
        vararg initial: ActiveExerciseUi,
        focused: Boolean = true,
    ) {
        exercises = initial.toList()
        focusedId = initial.first().id.takeIf { focused }
        composeRule.setContent { Screen() }
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
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
                    ),
                dayContext = null,
                focusedExerciseId = focusedId,
                onFocusExercise = { focusedId = it },
                onAddExercise = {},
                onCreateExercise = {},
                onOpenRecommendation = {},
                onRecordSet = { id, load, reps, seconds, rpe, warmup, pain, technique ->
                    recordedLoads += load
                    exercises =
                        exercises.map { item ->
                            if (item.id != id) {
                                item
                            } else {
                                val sets =
                                    item.sets +
                                        ActiveSetUi(
                                            id = WorkoutSetId("set-${item.sets.size + 1}"),
                                            setNumber = item.sets.size + 1,
                                            load = load,
                                            reps = reps,
                                            durationSeconds = seconds,
                                            rpe = rpe,
                                            isWarmup = warmup,
                                            pain = pain,
                                            techniqueQuality = technique,
                                        )
                                item.copy(sets = sets, seed = entrySeedOf(item.trackingType, sets, item.lastPerformance))
                            }
                        }
                },
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

    private fun text(
        @StringRes id: Int,
        vararg args: Any,
    ) = composeRule.activity.getString(id, *args)

    private fun stepWeightUp() {
        composeRule
            .onNodeWithContentDescription(text(R.string.workout_focus_more_weight))
            .performScrollTo()
            .performClick()
    }

    private fun logSet() {
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).performClick()
    }

    private fun assertSteppers(
        weight: String,
        reps: String,
    ) {
        composeRule.onNodeWithText(weight).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(reps).performScrollTo().assertIsDisplayed()
    }

    private fun goToBoardAndReopen(name: String) {
        composeRule.onNodeWithText(text(R.string.workout_focus_board)).performClick()
        composeRule.onNodeWithText(name).performClick()
    }

    @Test
    fun anUntouchedEntrySeedsFromTheLastSessionAndShowsLastTime() {
        start(exercise(last = lastTime, seed = lastTimeSeed))

        assertSteppers("80", "8")
        composeRule.onNodeWithText(text(R.string.workout_focus_last_time_weight_reps, "80", 8)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsEnabled()
    }

    @Test
    fun anExerciseNeverDoneStaysEmptyAndLogSetWaitsForWeightAndReps() {
        start(exercise())

        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(2)
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsNotEnabled()
        composeRule.onAllNodesWithText("Last time", substring = true).assertCountEquals(0)

        // GF-3: reps alone, or weight alone, are not enough; the plan's rep range is a caption only.
        stepRepsUp()
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsNotEnabled()
        stepWeightUp()
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsEnabled()
    }

    @Test
    fun lastTimeGivesWayToTheLoggedSetAfterTheFirstSet() {
        start(exercise(last = lastTime, seed = lastTimeSeed))

        logSet()

        composeRule.onAllNodesWithText("Last time", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Last: 80 kg × 8", substring = true).assertIsDisplayed()
    }

    @Test
    fun aLateArrivingSeedFillsAnUntouchedEntryAndNeverOverwritesATypedOne() {
        start(exercise())
        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(2)

        composeRule.runOnIdle { exercises = listOf(exercise(last = lastTime, seed = lastTimeSeed)) }
        assertSteppers("80", "8")

        stepWeightUp()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle {
            exercises = listOf(exercise(last = lastTime, seed = SetEntrySeed(load = BigDecimal("90"), reps = BigDecimal("10"))))
        }

        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("90").assertCountEquals(0)
    }

    @Test
    fun reopeningAfterTheBoardShowsThisSessionsNumbersNotLastSessions() {
        start(exercise(last = lastTime, seed = lastTimeSeed))
        stepWeightUp()
        logSet()
        composeRule.runOnIdle { assertEquals(listOf<Double?>(82.5), recordedLoads) }

        goToBoardAndReopen("Bench Press")

        assertSteppers("82.5", "8")
        composeRule.onAllNodesWithText("80").assertCountEquals(0)
    }

    @Test
    fun reopeningAfterNextAndTheBoardShowsThisSessionsNumbers() {
        start(
            exercise(last = lastTime, seed = lastTimeSeed),
            exercise(id = "e2", name = "Row"),
        )
        stepWeightUp()
        logSet()

        composeRule.onNodeWithText(text(R.string.workout_focus_next)).performClick()
        composeRule.runOnIdle { assertEquals(WorkoutExerciseId("e2"), focusedId) }
        goToBoardAndReopen("Bench Press")

        assertSteppers("82.5", "8")
    }

    @Test
    fun typedValuesSurviveRecreationWithTheSeedPresent() {
        val restoration = StateRestorationTester(composeRule)
        exercises = listOf(exercise(last = lastTime, seed = lastTimeSeed))
        focusedId = exercises.first().id
        restoration.setContent { Screen() }

        stepWeightUp()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("80").assertCountEquals(0)
    }

    @Test
    fun aTypedEntryStaysTypedWhenTheSeedArrivesAfterRecreation() {
        val restoration = StateRestorationTester(composeRule)
        exercises = listOf(exercise())
        focusedId = exercises.first().id
        restoration.setContent { Screen() }

        stepWeightUp()
        composeRule.onNodeWithText("2.5").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { exercises = listOf(exercise(last = lastTime, seed = lastTimeSeed)) }

        // Weight was typed (kept); reps were never touched, but the entry as a whole is touched.
        composeRule.onNodeWithText("2.5").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("80").assertCountEquals(0)
    }

    @Test
    fun anUntouchedEntryStillTakesALateSeedAfterRecreation() {
        val restoration = StateRestorationTester(composeRule)
        exercises = listOf(exercise())
        focusedId = exercises.first().id
        restoration.setContent { Screen() }

        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle { exercises = listOf(exercise(last = lastTime, seed = lastTimeSeed)) }

        assertSteppers("80", "8")
    }

    private fun stepRepsUp() {
        composeRule
            .onNodeWithContentDescription(text(R.string.workout_focus_more_reps))
            .performScrollTo()
            .performClick()
    }

    /** GF-1: a seeded exercise keeps what was stepped when the Board is visited. */
    @Test
    fun anUnloggedSeededEntryKeepsItsStepsAcrossTheBoard() {
        start(exercise(last = lastTime, seed = lastTimeSeed))
        stepWeightUp()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()

        goToBoardAndReopen("Bench Press")

        assertSteppers("82.5", "8")
        composeRule.onAllNodesWithText("80").assertCountEquals(0)
    }

    /** GF-1: a never-done exercise keeps both stepped values - it came back as empty before. */
    @Test
    fun anUnloggedNeverDoneEntryKeepsItsStepsAcrossTheBoard() {
        start(exercise())
        stepWeightUp()
        stepRepsUp()

        goToBoardAndReopen("Bench Press")

        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(0)
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsEnabled()
    }

    /** GF-1: each exercise keeps its own entry when focus moves to another exercise and back. */
    @Test
    fun eachExerciseKeepsItsOwnUnloggedEntryWhenFocusMovesAwayAndBack() {
        start(
            exercise(last = lastTime, seed = lastTimeSeed),
            exercise(id = "e2", name = "Row"),
        )
        stepWeightUp()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.workout_focus_next)).performClick()
        composeRule.runOnIdle { assertEquals(WorkoutExerciseId("e2"), focusedId) }
        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(2)
        stepRepsUp()

        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e1") }
        assertSteppers("82.5", "8")

        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e2") }
        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(1)
    }

    /** GF-1: the held entries are saved across recreation, after a Board visit as well. */
    @Test
    fun anUnloggedEntryKeptAcrossTheBoardSurvivesRecreation() {
        val restoration = StateRestorationTester(composeRule)
        exercises = listOf(exercise(last = lastTime, seed = lastTimeSeed))
        focusedId = exercises.first().id
        restoration.setContent { Screen() }
        stepWeightUp()
        goToBoardAndReopen("Bench Press")

        restoration.emulateSavedInstanceStateRestore()

        assertSteppers("82.5", "8")
    }

    /**
     * GXI4-I1: two cached untouched entries that receive the same late seed each take it - the seed effect
     * must restart on the entry change, not only on a seed change. A touched entry stays as typed.
     */
    @Test
    fun equalLateSeedsReachEveryCachedUntouchedEntryAndNeverATouchedOne() {
        start(
            exercise(id = "e1", name = "Bench Press"),
            exercise(id = "e2", name = "Row"),
        )
        // Visit both before any history arrives: two untouched entries, null seeds.
        composeRule.onNodeWithText(text(R.string.workout_focus_next)).performClick()
        composeRule.runOnIdle { assertEquals(WorkoutExerciseId("e2"), focusedId) }
        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e1") }
        composeRule.onAllNodesWithText(text(R.string.workout_focus_value_empty)).assertCountEquals(2)

        // History arrives for both, with identical seeds, while A is focused.
        composeRule.runOnIdle {
            exercises =
                listOf(
                    exercise(id = "e1", name = "Bench Press", last = lastTime, seed = lastTimeSeed),
                    exercise(id = "e2", name = "Row", last = lastTime, seed = lastTimeSeed),
                )
        }
        assertSteppers("80", "8")

        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e2") }
        assertSteppers("80", "8")

        // B is now touched; a later, different seed does not replace it.
        stepWeightUp()
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e1") }
        composeRule.runOnIdle { focusedId = WorkoutExerciseId("e2") }
        composeRule.onNodeWithText("82.5").performScrollTo().assertIsDisplayed()
    }
}
