package com.repflow.app.presentation.trainingplan.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.trainingplan.TrainingPlanStatusFilter
import com.repflow.app.domain.trainingplan.TrainingPlanId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Stateless Compose coverage for [TrainingPlanListScreen], mirroring
 * [com.repflow.app.presentation.exercise.list.ExerciseListScreenTest]'s
 * shape (Milestone 8, CP12 added the filter/archive/restore/snackbar
 * pieces). Remediation-1 CP11 moved archive/restore and edit (`Open`) off
 * the row menu onto `5a`'s card face, added the card's `Start workout`, and
 * moved create from the FAB onto the bottom action bar.
 */
@RunWith(AndroidJUnit4::class)
class TrainingPlanListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: TrainingPlanListUiState,
        onRetry: () -> Unit = {},
        onPlanClick: (TrainingPlanId) -> Unit = {},
        onStartClick: (TrainingPlanId) -> Unit = {},
        onCreateClick: () -> Unit = {},
        onFilterChanged: (TrainingPlanStatusFilter) -> Unit = {},
        onArchiveClicked: (TrainingPlanId) -> Unit = {},
        onRestoreClicked: (TrainingPlanId) -> Unit = {},
        onUndoArchiveClicked: (TrainingPlanId) -> Unit = {},
        onMessageShown: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            TrainingPlanListScreen(
                uiState = uiState,
                onRetry = onRetry,
                onPlanClick = onPlanClick,
                onStartClick = onStartClick,
                onCreateClick = onCreateClick,
                onFilterChanged = onFilterChanged,
                onArchiveClicked = onArchiveClicked,
                onRestoreClicked = onRestoreClicked,
                onUndoArchiveClicked = onUndoArchiveClicked,
                onMessageShown = onMessageShown,
            )
        }
    }

    @Test
    fun rendersContentRowsAndInvokesOnPlanClick() {
        var clickedId: TrainingPlanId? = null
        val item = planItem()
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Content(listOf(item))),
            onPlanClick = { clickedId = it },
        )

        composeRule.onNodeWithText("Push Pull Legs").assertIsDisplayed()
        composeRule.onNodeWithText("3 exercises · v2").assertIsDisplayed()
        composeRule.onNodeWithText("Push Pull Legs").performClick()

        assertEquals(item.id, clickedId)
    }

    @Test
    fun rendersTheEmptyState() {
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS)),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_empty))
            .assertIsDisplayed()
    }

    @Test
    fun rendersTheNoArchivedEmptyState() {
        setContent(
            uiState =
                TrainingPlanListUiState(
                    filter = TrainingPlanStatusFilter.ARCHIVED,
                    content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_ARCHIVED),
                ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_empty_no_archived))
            .assertIsDisplayed()
    }

    @Test
    fun rendersTheObservationFailedPanelAndInvokesRetry() {
        var retried = false
        setContent(
            uiState =
                TrainingPlanListUiState(
                    content = TrainingPlanListContent.ObservationFailed(TrainingPlanListFailureReason.UNKNOWN),
                ),
            onRetry = { retried = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_observation_failed))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_retry))
            .performClick()

        assertEquals(true, retried)
    }

    @Test
    fun createBarButtonClickInvokesOnCreateClick() {
        var created = false
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS)),
            onCreateClick = { created = true },
        )

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.training_plan_list_add_content_description),
            ).performClick()

        assertEquals(true, created)
    }

    @Test
    fun filterChipClickInvokesOnFilterChanged() {
        var selected: TrainingPlanStatusFilter? = null
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS)),
            onFilterChanged = { selected = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_filter_archived))
            .performClick()

        assertEquals(TrainingPlanStatusFilter.ARCHIVED, selected)
    }

    @Test
    fun cardOpenButtonInvokesOnPlanClick() {
        var clickedId: TrainingPlanId? = null
        val item = planItem()
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Content(listOf(item))),
            onPlanClick = { clickedId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_open))
            .performClick()

        assertEquals(item.id, clickedId)
    }

    @Test
    fun cardShowsArchiveWhenViewingActivePlansAndInvokesOnArchiveClicked() {
        var archivedId: TrainingPlanId? = null
        val item = planItem()
        setContent(
            uiState =
                TrainingPlanListUiState(
                    filter = TrainingPlanStatusFilter.ACTIVE,
                    content = TrainingPlanListContent.Content(listOf(item)),
                ),
            onArchiveClicked = { archivedId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_restore))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_archive))
            .performClick()

        assertEquals(item.id, archivedId)
    }

    @Test
    fun cardShowsRestoreWhenViewingArchivedPlansAndInvokesOnRestoreClicked() {
        var restoredId: TrainingPlanId? = null
        val item = planItem(archivedAt = Instant.parse("2026-02-02T12:00:00Z"))
        setContent(
            uiState =
                TrainingPlanListUiState(
                    filter = TrainingPlanStatusFilter.ARCHIVED,
                    content = TrainingPlanListContent.Content(listOf(item)),
                ),
            onRestoreClicked = { restoredId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_archive))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_start))
            .assertDoesNotExist()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_restore))
            .performClick()

        assertEquals(item.id, restoredId)
    }

    @Test
    fun cardStartButtonInvokesOnStartClickWithoutOpeningThePlan() {
        var startedId: TrainingPlanId? = null
        var opened = false
        val item = planItem()
        setContent(
            uiState = TrainingPlanListUiState(content = TrainingPlanListContent.Content(listOf(item))),
            onStartClick = { startedId = it },
            onPlanClick = { opened = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_card_start))
            .performClick()

        assertEquals(item.id, startedId)
        assertEquals(false, opened)
    }

    @Test
    fun archivedMessageShowsSnackbarWithUndoAndInvokesOnUndoArchiveClicked() {
        var undoneId: TrainingPlanId? = null
        var shownMessageId: Long? = null
        val planId = TrainingPlanId("1")
        setContent(
            uiState =
                TrainingPlanListUiState(
                    content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS),
                    messages = listOf(TrainingPlanListMessage.Archived(id = 1L, planId = planId)),
                ),
            onUndoArchiveClicked = { undoneId = it },
            onMessageShown = { shownMessageId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_message_archived))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_message_archived_undo))
            .performClick()

        assertEquals(planId, undoneId)
        assertEquals(1L, shownMessageId)
    }

    @Test
    fun operationFailedMessageShowsSnackbarAndConsumesItWithoutUndo() {
        var undoInvoked = false
        var shownMessageId: Long? = null
        setContent(
            uiState =
                TrainingPlanListUiState(
                    content = TrainingPlanListContent.Empty(TrainingPlanListEmptyReason.NO_PLANS),
                    messages = listOf(TrainingPlanListMessage.OperationFailed(id = 7L)),
                ),
            onUndoArchiveClicked = { undoInvoked = true },
            onMessageShown = { shownMessageId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.training_plan_list_message_operation_failed))
            .assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS) { shownMessageId != null }

        assertEquals(false, undoInvoked)
        assertEquals(7L, shownMessageId)
    }

    private fun planItem(
        id: String = "1",
        archivedAt: Instant? = null,
    ) = TrainingPlanListItem(
        id = TrainingPlanId(id),
        name = "Push Pull Legs",
        plannedExerciseCount = 3,
        versionNumber = 2,
        archivedAt = archivedAt,
    )

    private companion object {
        const val SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS = 8_000L
    }
}
