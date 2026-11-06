package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.history.BestSet
import com.repflow.app.application.history.PersonalBest
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The done screen's timed values read as m:ss (timed-entry-mmss CP3): the recap
 * group, the best set and what it beat, and the signed delta. The model behind
 * them still carries whole seconds, which `WorkoutDoneTest` pins.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDoneScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val endedAt = Instant.parse("2026-08-11T18:02:00Z")

    private fun setContent(
        recaps: List<RecapRowUi> = emptyList(),
        personalBests: List<PersonalBest> = emptyList(),
    ) {
        composeRule.setContent {
            RepFlowTheme {
                WorkoutDoneScreen(
                    uiState =
                        WorkoutDoneUiState(
                            content =
                                WorkoutDoneContent.Loaded(
                                    sessionId = WorkoutSessionId("session-1"),
                                    planName = null,
                                    startedAt = endedAt.minusSeconds(3_000),
                                    endedAt = endedAt,
                                    workingSets = 3,
                                    exercisesTrained = 1,
                                    exerciseCount = 1,
                                    personalBests = personalBests,
                                    recaps = recaps,
                                    recommendations = emptyList(),
                                ),
                        ),
                    onOpenRecommendation = {},
                    onBackToHome = {},
                )
            }
        }
    }

    private fun timedRow(
        seconds: List<Int>,
        delta: RecapDelta = RecapDelta.FirstTime,
    ) = RecapRowUi(
        id = WorkoutExerciseId("exercise-1"),
        name = "Plank",
        groups = listOf(RecapGroup.Timed(seconds)),
        workingSets = seconds.size,
        warmups = 0,
        delta = delta,
    )

    /** The screen is one lazy list, so a row has to be scrolled to before it can be found. */
    private fun assertShows(text: String) {
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun recapDetailOf(
        count: Int,
        clocks: String,
    ) = composeRule.activity.resources.getQuantityString(R.plurals.workout_done_recap_detail, count, count, clocks)

    @Test
    fun theRecapJoinsItsClocksWithCommas() {
        setContent(recaps = listOf(timedRow(listOf(90, 75, 60))))

        assertShows(recapDetailOf(3, "1:30, 1:15, 1:00"))
    }

    @Test
    fun theRecapReadsAsAClockUnderAMinute() {
        setContent(recaps = listOf(timedRow(listOf(45))))

        assertShows(recapDetailOf(1, "0:45"))
    }

    @Test
    fun theBestSetAndWhatItBeatReadAsClocks() {
        val previousEndedAt = Instant.parse("2026-08-05T18:00:00Z")
        setContent(
            personalBests =
                listOf(
                    PersonalBest(
                        exerciseId = ExerciseId("plank"),
                        exerciseName = "Plank",
                        best = BestSet.Seconds(90),
                        previous = BestSet.Seconds(45),
                        previousEndedAt = previousEndedAt,
                    ),
                ),
        )

        val locale = composeRule.activity.resources.configuration.locales[0]
        val date = DateTimeFormatter.ofPattern("d MMM", locale).format(previousEndedAt.atZone(ZoneId.systemDefault()))
        assertShows(composeRule.activity.getString(R.string.workout_done_best_set_detail, "1:30", "0:45", date))
    }

    @Test
    fun aLongerHoldReadsAsAClockWithAPlus() {
        setContent(recaps = listOf(timedRow(listOf(90), RecapDelta.Seconds(15))))

        assertShows("+0:15")
    }

    @Test
    fun aShorterHoldReadsAsAClockWithAMinus() {
        setContent(recaps = listOf(timedRow(listOf(60), RecapDelta.Seconds(-65))))

        assertShows("−1:05")
    }

    @Test
    fun theSameHoldStillReadsSameTime() {
        setContent(recaps = listOf(timedRow(listOf(60), RecapDelta.Seconds(0))))

        assertShows(composeRule.activity.getString(R.string.workout_done_delta_seconds_same))
    }
}
