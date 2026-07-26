package com.repflow.app.presentation.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.domain.workout.WorkoutSetId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** Stateless Compose coverage for [HistoryDetailScreen] - surfacing every recordable set field (Milestone 8, CP13). */
@RunWith(AndroidJUnit4::class)
class HistoryDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun sessionWithSet(set: WorkoutSet): WorkoutSession {
        val exercise =
            (
                WorkoutExercise.create(
                    id = WorkoutExerciseId("we-1"),
                    sessionId = WorkoutSessionId("session-1"),
                    exerciseId = ExerciseId("ex-1"),
                    order = 0,
                    exerciseNameSnapshot = "Bench Press",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                    sets = listOf(set),
                ) as DomainResult.Success
            ).value
        val started = WorkoutSession.start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
        val withExercise = (started.withAddedExercise(exercise) as DomainResult.Success).value
        return (withExercise.complete(Instant.parse("2026-01-01T01:00:00Z")) as DomainResult.Success).value
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
        val session = sessionWithSet(set)

        composeRule.setContent {
            HistoryDetailScreen(session = session, onBackClick = {})
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_warmup_suffix), substring = true)
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
        val session = sessionWithSet(set)

        composeRule.setContent {
            HistoryDetailScreen(session = session, onBackClick = {})
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_set_duration, 45))
            .assertIsDisplayed()
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
        val session = sessionWithSet(set)
        var backClicked = false

        composeRule.setContent {
            HistoryDetailScreen(session = session, onBackClick = { backClicked = true })
        }

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_detail_back))
            .performClick()

        assertEquals(true, backClicked)
    }
}
