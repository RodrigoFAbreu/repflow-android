package com.repflow.app.presentation.history

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Stateless Compose coverage for [HistoryScreen] - state in, events out, no
 * Hilt, mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreenTest].
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
        onMessageShown: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            HistoryScreen(
                uiState = uiState,
                onSessionClick = onSessionClick,
                onDetailDismissed = onDetailDismissed,
                onInvalidateClicked = onInvalidateClicked,
                onMessageShown = onMessageShown,
            )
        }
    }

    @Test
    fun rendersContentRows() {
        setContent(HistoryUiState(isLoading = false, sessions = listOf(completedSession())))

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_session_invalidate_action))
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

        val dateText = session.startedAt.atZone(ZoneId.systemDefault()).format(rowDateFormatter)
        composeRule.onNodeWithText(dateText).performClick()

        assertEquals(session.id, clickedId)
    }

    @Test
    fun invalidateActionShowsConfirmationDialogBeforeInvoking() {
        var invalidatedId: WorkoutSessionId? = null
        val session = completedSession()
        setContent(
            HistoryUiState(isLoading = false, sessions = listOf(session)),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_session_invalidate_action))
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
            HistoryUiState(isLoading = false, sessions = listOf(session)),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_session_invalidate_action))
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
            HistoryUiState(isLoading = false, sessions = listOf(session)),
            onInvalidateClicked = { invalidatedId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.history_session_invalidate_action))
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

    private companion object {
        const val SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS = 8_000L
        val rowDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
    }
}
