package com.repflow.app.presentation.workout

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Remediation-1 CP14 item 4: `Keep screen awake in a workout` really keeps the
 * screen on. With the switch on, the workout surface - board or focus mode -
 * sets `keepScreenOn` on its view; off, it sets nothing; and the flag is
 * cleared when the surface leaves composition.
 */
@RunWith(AndroidJUnit4::class)
class KeepScreenAwakeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View

    private fun render(
        keepScreenAwake: Boolean,
        focused: Boolean,
        showSurface: () -> Boolean = { true },
    ) {
        val exercise =
            ActiveExerciseUi(
                id = WorkoutExerciseId("exercise-1"),
                name = "Bench Press",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                sets = emptyList(),
                plannedTarget = null,
            )
        composeRule.setContent {
            view = LocalView.current
            RepFlowTheme {
                if (showSurface()) {
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
                        focusedExerciseId = if (focused) exercise.id else null,
                        onFocusExercise = {},
                        onAddExercise = {},
                        onCreateExercise = {},
                        onOpenRecommendation = {},
                        onRecordSet = { _, _, _, _, _, _, _, _ -> },
                        onUndoLastSet = {},
                        onEditLastSet = { _, _, _, _, _, _, _, _ -> },
                        onAddRestTime = {},
                        onRemoveRestTime = {},
                        onSkipRestTimer = {},
                        onCompleteWorkout = {},
                        onLeaveWorkout = {},
                        onAbandonWorkout = {},
                        onRetry = {},
                        keepScreenAwake = keepScreenAwake,
                    )
                }
            }
        }
    }

    @Test
    fun switchOnKeepsTheBoardsScreenOn() {
        render(keepScreenAwake = true, focused = false)
        composeRule.runOnIdle { assertTrue(view.keepScreenOn) }
    }

    @Test
    fun switchOnKeepsFocusModesScreenOn() {
        render(keepScreenAwake = true, focused = true)
        composeRule.runOnIdle { assertTrue(view.keepScreenOn) }
    }

    @Test
    fun switchOffLeavesTheScreenFree() {
        render(keepScreenAwake = false, focused = false)
        composeRule.runOnIdle { assertFalse(view.keepScreenOn) }
    }

    @Test
    fun leavingTheWorkoutSurfaceClearsTheFlag() {
        var shown by mutableStateOf(true)
        render(keepScreenAwake = true, focused = false, showSurface = { shown })
        composeRule.runOnIdle { assertTrue(view.keepScreenOn) }

        shown = false

        composeRule.runOnIdle { assertFalse(view.keepScreenOn) }
    }
}
