package com.repflow.app.presentation.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.repflow.app.presentation.RepFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Stateless Compose coverage for [HistoryScreen] - state in, events out, no
 * Hilt, mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreenTest].
 *
 * Remediation-1 CP12 moved invalidating a workout off the list row to the
 * detail's `⋮` (`3a`, `askInvalidate`), so the three invalidate tests open the
 * detail first (a selected session) and reach the same dialog from there.
 */
@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun completedSession(id: String = "session-1"): WorkoutSession {
        val started = WorkoutSession.start(WorkoutSessionId(id), null, Instant.parse("2026-01-01T00:00:00Z"))
        return when (val result = started.complete(Instant.parse("2026-01-01T01:00:00Z"))) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }
    }

    private fun setContent(
        uiState: HistoryUiState,
        onSessionClick: (WorkoutSessionId) -> Unit = {},
        onDetailDismissed: () -> Unit = {},
        onInvalidateClicked: (WorkoutSessionId) -> Unit = {},
        onExerciseFilterChanged: (ExerciseId?) -> Unit = {},
        onPlanFilterChanged: (HistoryPlanFilter) -> Unit = {},
        onStartDateChanged: (LocalDate?) -> Unit = {},
        onEndDateChanged: (LocalDate?) -> Unit = {},
        onShowInvalidatedChanged: (Boolean) -> Unit = {},
        onSortOrderChanged: (HistorySortOrder) -> Unit = {},
        onMessageShown: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                HistoryScreen(
                    uiState = uiState,
                    onSessionClick = onSessionClick,
                    onDetailDismissed = onDetailDismissed,
                    onInvalidateClicked = onInvalidateClicked,
                    onExerciseFilterChanged = onExerciseFilterChanged,
                    onPlanFilterChanged = onPlanFilterChanged,
                    onStartDateChanged = onStartDateChanged,
                    onEndDateChanged = onEndDateChanged,
                    onShowInvalidatedChanged = onShowInvalidatedChanged,
                    onSortOrderChanged = onSortOrderChanged,
                    onMessageShown = onMessageShown,
                )
            }
        }
    }

    @Test
    fun rendersContentRows() {
        setContent(HistoryUiState(isLoading = false, sessions = listOf(completedSession())))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.home_untitled_workout))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_duration_minutes, 60), substring = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.resources.getQuantityString(R.plurals.history_count, 1, 1))
            .assertIsDisplayed()
        // The row face carries no destructive action any more: it lives on the detail.
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.history_session_invalidate_action))
            .assertDoesNotExist()
    }

    @Test
    fun rowsCarryThePrAndInvalidatedBadges() {
        val personalBest = completedSession("session-1")
        val invalidated =
            when (val result = completedSession("session-2").invalidate(Instant.parse("2026-01-02T00:00:00Z"))) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
            }
        setContent(
            HistoryUiState(
                isLoading = false,
                sessions = listOf(personalBest, invalidated),
                personalBestSessionIds = setOf(personalBest.id),
                filters = HistoryFilters(showInvalidated = true),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_row_badge_pr))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_row_badge_invalidated))
            .assertIsDisplayed()
    }

    @Test
    fun rowClickInvokesOnSessionClick() {
        var clickedId: WorkoutSessionId? = null
        val session = completedSession()
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(session)),
            onSessionClick = { clickedId = it },
        )

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_untitled_workout)).performClick()

        assertEquals(session.id, clickedId)
    }

    @Test
    fun invalidateActionShowsConfirmationDialogBeforeInvoking() {
        var invalidatedId: WorkoutSessionId? = null
        val session = completedSession()
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(session), selectedSessionId = session.id),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.history_session_invalidate_action))
            .performClick()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_invalidate_dialog_title))
            .assertIsDisplayed()
        assertNull(invalidatedId)
    }

    @Test
    fun confirmingTheDialogInvokesOnInvalidateClicked() {
        var invalidatedId: WorkoutSessionId? = null
        val session = completedSession()
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(session), selectedSessionId = session.id),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.history_session_invalidate_action))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_invalidate_dialog_confirm))
            .performClick()

        assertEquals(session.id, invalidatedId)
    }

    @Test
    fun cancelingTheDialogDoesNotInvokeOnInvalidateClicked() {
        var invalidatedId: WorkoutSessionId? = null
        val session = completedSession()
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(session), selectedSessionId = session.id),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.history_session_invalidate_action))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_invalidate_dialog_cancel))
            .performClick()

        assertNull(invalidatedId)
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_invalidate_dialog_title))
            .assertDoesNotExist()
    }

    @Test
    fun invalidatedMessageShowsSnackbarAndConsumesIt() {
        var shownMessageId: Long? = null
        setContent(
            HistoryUiState(
                isLoading = false,
                messages = listOf(HistoryMessage.Invalidated(id = 3L)),
            ),
            onMessageShown = { shownMessageId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_message_invalidated))
            .assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS) { shownMessageId != null }

        assertEquals(3L, shownMessageId)
    }

    @Test
    fun sortOrderButtonTogglesBetweenNewestAndOldestFirst() {
        var sortOrder: HistorySortOrder? = null
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(completedSession())),
            onSortOrderChanged = { sortOrder = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_filter_sort_newest))
            .performClick()

        assertEquals(HistorySortOrder.OLDEST_FIRST, sortOrder)
    }

    @Test
    fun showInvalidatedChipInvokesOnShowInvalidatedChanged() {
        var showInvalidated: Boolean? = null
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(completedSession())),
            onShowInvalidatedChanged = { showInvalidated = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_filter_show_invalidated))
            .performClick()

        assertEquals(true, showInvalidated)
    }

    private fun completedSessionWithExercise(): WorkoutSession {
        val exercise =
            (
                WorkoutExercise.create(
                    id = WorkoutExerciseId("we-1"),
                    sessionId = WorkoutSessionId("session-1"),
                    exerciseId = ExerciseId("bench-press"),
                    order = 0,
                    exerciseNameSnapshot = "Bench Press",
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    plannedExerciseId = null,
                ) as DomainResult.Success
            ).value
        val started = WorkoutSession.start(WorkoutSessionId("session-1"), null, Instant.parse("2026-01-01T00:00:00Z"))
        val withExercise = (started.withAddedExercise(exercise) as DomainResult.Success).value
        return (withExercise.complete(Instant.parse("2026-01-01T01:00:00Z")) as DomainResult.Success).value
    }

    @Test
    fun exerciseFilterSheetInvokesOnExerciseFilterChanged() {
        var selectedExerciseId: ExerciseId? = null
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(completedSessionWithExercise())),
            onExerciseFilterChanged = { selectedExerciseId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_filter_exercise_all))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_filter_exercise_sheet_title).uppercase())
            .assertIsDisplayed()
        composeRule.onNodeWithText("Bench Press").performClick()

        assertEquals(ExerciseId("bench-press"), selectedExerciseId)
    }

    private companion object {
        const val SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS = 8_000L
    }
}
