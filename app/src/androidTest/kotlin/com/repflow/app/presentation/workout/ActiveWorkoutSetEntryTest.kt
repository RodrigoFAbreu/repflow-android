package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 *
 * `timed-set-clock` CP3 uses the same harness for a running clock across the Board
 * and a switch of exercise, and for `Start` ending a rest: it can hold a rest, and it
 * counts `onSkipRestTimer`, which ends the rest as the real use case does.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutSetEntryTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var exercises by mutableStateOf<List<ActiveExerciseUi>>(emptyList())
    private var focusedId by mutableStateOf<WorkoutExerciseId?>(null)
    private val recordedLoads = mutableListOf<Double?>()
    private var saveOutcome = SaveOutcome.SUCCEEDS
    private var heldOnSaved: (() -> Unit)? = null
    private var restTimer by mutableStateOf<RestTimerUi?>(null)
    private var sessionId by mutableStateOf("session-1")
    private var skipCalls = 0

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

    private fun timedExercise(
        id: String = "t1",
        name: String = PLANK,
    ) = ActiveExerciseUi(
        id = WorkoutExerciseId(id),
        name = name,
        trackingType = ExerciseTrackingType.DURATION,
        sets = emptyList(),
        plannedTarget = PlannedTargetUi(targetWarmupSets = null, targetWorkingSets = 3, durationRangeSeconds = 60L..60L),
        lastPerformance = null,
        seed = null,
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
    private fun Screen(focusEntries: FocusEntries = rememberSaveable(saver = FocusEntries.Saver) { FocusEntries() }) {
        RepFlowTheme {
            ActiveWorkoutScreen(
                focusEntries = focusEntries,
                uiState =
                    ActiveWorkoutUiState(
                        content =
                            ActiveWorkoutContent.Active(
                                sessionId = WorkoutSessionId(sessionId),
                                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                                exercises = exercises,
                                restTimer = restTimer,
                            ),
                    ),
                dayContext = null,
                focusedExerciseId = focusedId,
                onFocusExercise = { focusedId = it },
                onAddExercise = {},
                onCreateExercise = {},
                onOpenRecommendation = {},
                onRecordSet = { id, load, reps, seconds, rpe, warmup, pain, technique, onSaved ->
                    recordedLoads += load
                    when (saveOutcome) {
                        SaveOutcome.FAILS -> {
                            Unit
                        }

                        SaveOutcome.HELD -> {
                            heldOnSaved = onSaved
                        }

                        SaveOutcome.SUCCEEDS -> {
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
                            onSaved()
                        }
                    }
                },
                onUndoLastSet = {},
                onEditSet = { _, _, _, _, _, _, _, _, _ -> },
                onAddRestTime = {},
                onRemoveRestTime = {},
                onSkipRestTimer = {
                    skipCalls++
                    restTimer = null
                },
                onCompleteWorkout = {},
                onLeaveWorkout = {},
                onAbandonWorkout = {},
                onRetry = {},
                onErrorShown = {},
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

    /** A-1: a save that fails leaves the entry exactly as typed, so `Log set` can be pressed again. */
    @Test
    fun aFailedSaveKeepsEveryDetailValue() {
        saveOutcome = SaveOutcome.FAILS
        start(exercise(last = lastTime, seed = lastTimeSeed))
        enterAllDetail()

        composeRule.onNodeWithText(text(R.string.workout_focus_log_warmup)).performClick()

        assertRpeSelected("7")
        assertPainSelected("2")
        assertTechniqueSelected("3")
        composeRule.onNodeWithText(text(R.string.workout_focus_log_warmup)).assertIsDisplayed()
    }

    /** A-1: the existing rule, now reached through `onSaved`: the detail clears, and weight and reps stay. */
    @Test
    fun aSuccessfulSaveClearsTheDetailAndKeepsWeightAndReps() {
        start(exercise(last = lastTime, seed = lastTimeSeed))
        enterAllDetail()

        composeRule.onNodeWithText(text(R.string.workout_focus_log_warmup)).performClick()

        assertNoneSelected()
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsDisplayed()
        // The open RPE row also holds an 8, so the reps stepper is the one that isn't selectable.
        composeRule.onNodeWithText("80").performScrollTo().assertIsDisplayed()
        composeRule.onNode(hasText("8") and !isSelectable()).performScrollTo().assertIsDisplayed()
    }

    /**
     * A-1: the entry stays editable while the save suspends. What changed since the tap is not what that set
     * saved, so it stays. Fails with an unconditional clear, and with a snapshot taken when the callback runs.
     */
    @Test
    fun aValueChangedDuringTheSaveSurvivesItAndTheRestClears() {
        saveOutcome = SaveOutcome.HELD
        start(exercise(last = lastTime, seed = lastTimeSeed))
        composeRule.onNodeWithText(text(R.string.workout_active_set_detail_toggle)).performScrollTo().performClick()
        selectRpe("7")
        composeRule.onNodeWithText(text(R.string.workout_active_warmup_label)).performScrollTo().performClick()

        composeRule.onNodeWithText(text(R.string.workout_focus_log_warmup)).performClick()
        // Nothing is cleared while the save is still running.
        assertRpeSelected("7")
        composeRule.onNodeWithText(text(R.string.workout_focus_log_warmup)).assertIsDisplayed()
        selectRpe("8")
        composeRule.runOnIdle { checkNotNull(heldOnSaved).invoke() }

        assertRpeSelected("8")
        composeRule.onNodeWithText(text(R.string.workout_focus_log_set)).assertIsDisplayed()
    }

    /** CP3's test 1: the clock stays with its exercise, and the other exercise has its own. */
    @Test
    fun aRunningClockSurvivesSwitchingExerciseAndStaysWithItsOwn() {
        start(timedExercise(), timedExercise(id = "t2", name = "Side Plank"))
        tap(R.string.workout_focus_clock_start)
        val before = composeRule.waitForClockReadout(atLeastSeconds = 2)

        composeRule.onNodeWithText(text(R.string.workout_focus_next)).performClick()
        composeRule.runOnIdle { assertEquals(WorkoutExerciseId("t2"), focusedId) }
        composeRule.onNodeWithText(text(R.string.workout_focus_clock_stop)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.workout_focus_clock_start)).performScrollTo().assertIsDisplayed()

        goToBoardAndReopen(PLANK)

        composeRule.onNodeWithText(text(R.string.workout_focus_clock_stop)).performScrollTo().assertIsDisplayed()
        assertTrue("The readout must not drop across a switch", composeRule.clockReadoutSeconds() >= before)
    }

    /** CP3's test 2. */
    @Test
    fun aRunningClockSurvivesATripToTheBoard() {
        start(timedExercise())
        tap(R.string.workout_focus_clock_start)
        val before = composeRule.waitForClockReadout(atLeastSeconds = 2)

        goToBoardAndReopen(PLANK)

        composeRule.onNodeWithText(text(R.string.workout_focus_clock_stop)).performScrollTo().assertIsDisplayed()
        assertTrue("The readout must not drop across the Board", composeRule.clockReadoutSeconds() >= before)
    }

    /** CP3's test 3, `20r`: `Start` and `Start again` end a rest, and `Stop` does neither that nor log a set. */
    @Test
    fun startAndStartAgainEndARunningRestAndStopDoesNot() {
        restTimer = runningRest()
        start(timedExercise())
        composeRule.onNodeWithText(text(R.string.workout_rest_resting)).assertIsDisplayed()

        tap(R.string.workout_focus_clock_start)
        composeRule.runOnIdle { assertEquals(1, skipCalls) }
        composeRule.onNodeWithText(text(R.string.workout_rest_resting)).assertDoesNotExist()
        composeRule.waitForClockReadout(atLeastSeconds = 1)

        // A rest can run alongside a clock, from a set logged on another exercise. `Stop` leaves it.
        composeRule.runOnIdle { restTimer = runningRest() }
        tap(R.string.workout_focus_clock_stop)
        composeRule.runOnIdle {
            assertEquals(1, skipCalls)
            assertTrue("Stop must not log a set", recordedLoads.isEmpty())
        }
        composeRule.onNodeWithText(text(R.string.workout_rest_resting)).assertIsDisplayed()

        tap(R.string.workout_focus_clock_start_again)
        composeRule.runOnIdle { assertEquals(2, skipCalls) }
        composeRule.onNodeWithText(text(R.string.workout_rest_resting)).assertDoesNotExist()
    }

    /** CP3's test 3: a skip with no rest would still write the session. */
    @Test
    fun startAndStartAgainWithNoRestRunningEndNothing() {
        start(timedExercise())

        tap(R.string.workout_focus_clock_start)
        composeRule.waitForClockReadout(atLeastSeconds = 1)
        tap(R.string.workout_focus_clock_stop)
        tap(R.string.workout_focus_clock_start_again)

        composeRule.onNodeWithText(text(R.string.workout_focus_clock_stop)).performScrollTo().assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, skipCalls) }
    }

    /** A case the boards don't draw: the rest is over and the next set has started, so `Rest done` goes too. */
    @Test
    fun startClearsARestDoneStillShowing() {
        restTimer = RestTimerUi(endAt = Instant.now().minusSeconds(5), totalDurationSeconds = REST_SECONDS)
        start(timedExercise())
        composeRule.onNodeWithText(text(R.string.workout_rest_done)).assertIsDisplayed()

        tap(R.string.workout_focus_clock_start)

        composeRule.runOnIdle { assertEquals(1, skipCalls) }
        composeRule.onNodeWithText(text(R.string.workout_rest_done)).assertDoesNotExist()
    }

    /** The screen really runs [FocusEntries.retainOnly], which `SetEntryStateTest` tests alone, once a new session opens. */
    @Test
    fun aNewSessionDropsAnEarlierSessionsEntriesFromTheHolder() {
        val holder = FocusEntries()
        exercises = listOf(timedExercise())
        focusedId = exercises.first().id
        composeRule.setContent { Screen(holder) }
        tap(R.string.workout_focus_clock_start)
        composeRule.runOnIdle { assertNotNull(holder.entryFor("t1", seed = null).clockStartedAtMillis) }

        composeRule.runOnIdle {
            sessionId = "session-2"
            exercises = listOf(timedExercise(id = "t2", name = "Side Plank"))
            focusedId = WorkoutExerciseId("t2")
        }

        composeRule.runOnIdle { assertNull(holder.entryFor("t1", seed = null).clockStartedAtMillis) }
    }

    /** A replaced button takes TalkBack's focus with it, and the walk heard it land on an unrelated row after `Start`. */
    @Test
    fun startAndStopAreOneNodeSoAScreenReadersFocusStaysOnTheButton() {
        start(timedExercise())
        val startNode = composeRule.onNodeWithText(text(R.string.workout_focus_clock_start)).fetchSemanticsNode().id

        tap(R.string.workout_focus_clock_start)

        assertEquals(startNode, composeRule.onNodeWithText(text(R.string.workout_focus_clock_stop)).fetchSemanticsNode().id)
    }

    private fun runningRest() = RestTimerUi(endAt = Instant.now().plusSeconds(REST_SECONDS.toLong()), totalDurationSeconds = REST_SECONDS)

    private fun tap(
        @StringRes id: Int,
    ) {
        composeRule.onNodeWithText(text(id)).performScrollTo().performClick()
    }

    private fun enterAllDetail() {
        composeRule.onNodeWithText(text(R.string.workout_active_set_detail_toggle)).performScrollTo().performClick()
        selectRpe("7")
        // Pain and technique share 0-5 with RPE's first line, so each is the row's own node by index.
        composeRule.onAllNodes(hasText("2") and isSelectable())[PAIN_ROW].performScrollTo().performClick()
        composeRule.onAllNodes(hasText("3") and isSelectable())[TECHNIQUE_ROW].performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.workout_active_warmup_label)).performScrollTo().performClick()
    }

    private fun selectRpe(value: String) {
        composeRule.onNode(hasText(value) and isSelectable()).performScrollTo().performClick()
    }

    private fun assertRpeSelected(value: String) {
        composeRule.onNode(hasText(value) and isSelectable()).performScrollTo().assertIsSelected()
    }

    private fun assertPainSelected(value: String) {
        composeRule.onAllNodes(hasText(value) and isSelectable())[PAIN_ROW].performScrollTo().assertIsSelected()
    }

    private fun assertTechniqueSelected(value: String) {
        composeRule.onAllNodes(hasText(value) and isSelectable())[TECHNIQUE_ROW].performScrollTo().assertIsSelected()
    }

    private fun assertNoneSelected() {
        composeRule.onNode(hasText("7") and isSelectable()).performScrollTo().assertIsNotSelected()
        composeRule.onAllNodes(isSelectable() and isSelected()).assertCountEquals(0)
    }

    private enum class SaveOutcome { SUCCEEDS, FAILS, HELD }

    private companion object {
        /** Among the nodes with one number on 0-5: RPE's first line, then pain, then technique. */
        const val PAIN_ROW = 1
        const val TECHNIQUE_ROW = 2
        const val PLANK = "Plank"
        const val REST_SECONDS = 90
    }
}

/** The clock's readout, which is on the real clock, so what is asserted about it is a bound. */
internal fun ComposeTestRule.clockReadoutSeconds(): Long {
    val (minutes, seconds) =
        onNodeWithTag(TIMED_SET_CLOCK_READOUT_TAG)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .single()
            .text
            .split(":")
    return minutes.toLong() * SECONDS_PER_MINUTE + seconds.toLong()
}

/** Waits on the real clock, with no sleep and no time asserted exactly, and returns what the readout reads. */
internal fun ComposeTestRule.waitForClockReadout(atLeastSeconds: Long): Long {
    waitUntil(CLOCK_READOUT_TIMEOUT_MILLIS) { clockReadoutSeconds() >= atLeastSeconds }
    return clockReadoutSeconds()
}

private const val SECONDS_PER_MINUTE = 60L
private const val CLOCK_READOUT_TIMEOUT_MILLIS = 20_000L
