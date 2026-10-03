package com.repflow.app.presentation.exercise.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.designsystem.components.EMPTY_STATE_GLYPH_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stateless Compose coverage for [ExerciseListScreen] - state in, events
 * out, no Hilt (see plan.md section H / L). [createAndroidComposeRule] is
 * used only for `.activity.getString(...)` access to real string
 * resources - the activity under test still renders nothing but this
 * screen's own content, no Hilt-provided ViewModel is involved.
 *
 * The content is wrapped in [RepFlowTheme] so these tests render the same
 * scheme/token combination production does. Without it the screen would take
 * Material 3's baseline scheme while `LocalRepFlowExtraColors` had no
 * provider at all - a combination that cannot occur in the app, and one the
 * local's failing default now rejects outright.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseListScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        uiState: ExerciseListUiState,
        onQueryChanged: (String) -> Unit = {},
        onFilterChanged: (ExerciseStatusFilter) -> Unit = {},
        onRetry: () -> Unit = {},
        onExerciseClick: (ExerciseId) -> Unit = {},
        onCreateClick: () -> Unit = {},
        onCreateFromQueryClick: (String) -> Unit = {},
        onArchiveClicked: (ExerciseId) -> Unit = {},
        onRestoreClicked: (ExerciseId) -> Unit = {},
        onUndoArchiveClicked: (ExerciseId) -> Unit = {},
        onMessageShown: (Long) -> Unit = {},
        onBackClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            RepFlowTheme {
                ExerciseListScreen(
                    uiState = uiState,
                    onQueryChanged = onQueryChanged,
                    onFilterChanged = onFilterChanged,
                    onRetry = onRetry,
                    onExerciseClick = onExerciseClick,
                    onCreateClick = onCreateClick,
                    onCreateFromQueryClick = onCreateFromQueryClick,
                    onArchiveClicked = onArchiveClicked,
                    onRestoreClicked = onRestoreClicked,
                    onUndoArchiveClicked = onUndoArchiveClicked,
                    onMessageShown = onMessageShown,
                    onBackClick = onBackClick,
                )
            }
        }
    }

    /** `2c`'s row: the name over `<type> · rest m:ss · in N plans` (remediation-1 CP10). */
    @Test
    fun rendersContentRows() {
        val item =
            ExerciseListItem(
                id = ExerciseId("1"),
                name = "Bench Press",
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                defaultRestSeconds = 90,
                defaultLoadIncrementGrams = 2_500,
                planUsageCount = 2,
            )
        setContent(ExerciseListUiState(content = ExerciseListContent.Content(listOf(item))))

        composeRule.onNodeWithText("Bench Press").assertIsDisplayed()
        val usage = composeRule.activity.resources.getQuantityString(R.plurals.exercise_list_meta_plan_usage, 2, 2)
        composeRule.onNodeWithText("Weight & reps · rest 1:30 · $usage").assertIsDisplayed()
    }

    @Test
    fun rendersTheNoExercisesEmptyState() {
        setContent(
            ExerciseListUiState(
                content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES),
            ),
        )

        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.exercise_list_empty_no_exercises),
            ).assertIsDisplayed()
        composeRule.onNodeWithTag(EMPTY_STATE_GLYPH_TAG).assertIsDisplayed()
    }

    /**
     * GF-2 (design `7c` N13): a query matching nothing on Active shows the
     * magnifier and `Nothing called "<query>". Create it below.` with
     * `Create "<query>"` under it, and tapping it reaches the create route
     * carrying the query.
     */
    @Test
    fun rendersTheNoSearchResultsEmptyState() {
        var createdFrom: String? = null
        setContent(
            ExerciseListUiState(
                query = "zzz",
                content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_SEARCH_RESULTS),
            ),
            onCreateFromQueryClick = { createdFrom = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_empty_no_match_active, "zzz"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EMPTY_STATE_GLYPH_TAG).assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_create_from_query, "zzz"))
            .assertIsDisplayed()
            .performClick()

        assertEquals("zzz", createdFrom)
    }

    @Test
    fun rendersTheNoArchivedEmptyState() {
        setContent(
            ExerciseListUiState(
                filter = ExerciseStatusFilter.ARCHIVED,
                content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_ARCHIVED),
            ),
        )

        composeRule
            .onNodeWithText(
                composeRule.activity.getString(R.string.exercise_list_empty_no_archived),
            ).assertIsDisplayed()
        composeRule.onNodeWithTag(EMPTY_STATE_GLYPH_TAG).assertIsDisplayed()
    }

    /**
     * GF-2: on Archived a query matching nothing says no archived exercises match it, with the
     * magnifier, no create action - and not `No archived exercises.`.
     */
    @Test
    fun theNoSearchResultsEmptyStateOnTheArchivedFilterNamesTheQueryAndOffersNoCreate() {
        setContent(
            ExerciseListUiState(
                query = "zzz",
                filter = ExerciseStatusFilter.ARCHIVED,
                content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_SEARCH_RESULTS),
            ),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_empty_no_match_archived, "zzz"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(EMPTY_STATE_GLYPH_TAG).assertIsDisplayed()
        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.exercise_list_empty_no_archived))
            .assertCountEquals(0)
        composeRule
            .onAllNodesWithText(composeRule.activity.getString(R.string.exercise_list_create_from_query, "zzz"))
            .assertCountEquals(0)
    }

    /** B5: the Library's search is `2c`'s 48dp, not Material's 56dp. */
    @Test
    fun theSearchFieldIsExactly48dpTall() {
        setContent(
            ExerciseListUiState(content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)),
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_search_hint))
            .assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun rendersTheObservationFailedPanelAndInvokesRetry() {
        var retried = false
        setContent(
            uiState =
                ExerciseListUiState(
                    content = ExerciseListContent.ObservationFailed(ExerciseListFailureReason.UNKNOWN),
                ),
            onRetry = { retried = true },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_observation_failed))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_retry))
            .performClick()

        assertEquals(true, retried)
    }

    @Test
    fun filterChipClickInvokesOnFilterChanged() {
        var selected: ExerciseStatusFilter? = null
        setContent(
            uiState = ExerciseListUiState(content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)),
            onFilterChanged = { selected = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_filter_archived))
            .performClick()

        assertEquals(ExerciseStatusFilter.ARCHIVED, selected)
    }

    @Test
    fun searchFieldInputInvokesOnQueryChanged() {
        var query: String? = null
        setContent(
            uiState = ExerciseListUiState(content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)),
            onQueryChanged = { query = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_search_hint))
            .performTextInput("bench")

        assertEquals("bench", query)
    }

    /*
     * The search-clear button is an affordance CP5 introduced - it did not
     * exist before this milestone - so "a surface whose behaviour did not
     * change" does not cover it. Two properties: it appears only once there
     * is something to clear, and it clears through the existing
     * `onQueryChanged("")` rather than a new event.
     */

    @Test
    fun theSearchClearButtonAppearsOnlyForANonEmptyQuery() {
        setContent(
            uiState =
                ExerciseListUiState(
                    query = "",
                    content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES),
                ),
        )

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_search_clear_content_description),
            ).assertDoesNotExist()
    }

    @Test
    fun tappingTheSearchClearButtonInvokesOnQueryChangedWithAnEmptyQuery() {
        var query: String? = null
        setContent(
            uiState =
                ExerciseListUiState(
                    query = "bench",
                    content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_SEARCH_RESULTS),
                ),
            onQueryChanged = { query = it },
        )

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_search_clear_content_description),
            ).assertIsDisplayed()
            .performClick()

        assertEquals("", query)
    }

    /** Create sits on the bottom action bar (`D35`), under the FAB's own accessible name. */
    @Test
    fun createBarButtonClickInvokesOnCreateClick() {
        var created = false
        setContent(
            uiState = ExerciseListUiState(content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES)),
            onCreateClick = { created = true },
        )

        composeRule
            .onNodeWithContentDescription(
                composeRule.activity.getString(R.string.exercise_list_add_content_description),
            ).assertIsDisplayed()
            .performClick()

        assertEquals(true, created)
    }

    /*
     * The row's `⋮` keeps its content description; since remediation-1 CP10 it
     * opens a row-action sheet instead of a `DropdownMenu`, so the second click
     * in each of the three tests below lands on the sheet's row of the same
     * words.
     */

    @Test
    fun rowMenuEditItemInvokesOnExerciseClick() {
        var clickedId: ExerciseId? = null
        val item = exerciseItem()
        setContent(
            uiState = ExerciseListUiState(content = ExerciseListContent.Content(listOf(item))),
            onExerciseClick = { clickedId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.exercise_list_row_menu_content_description))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_row_menu_edit))
            .performClick()

        assertEquals(item.id, clickedId)
    }

    @Test
    fun rowMenuShowsArchiveWhenViewingActiveExercisesAndInvokesOnArchiveClicked() {
        var archivedId: ExerciseId? = null
        val item = exerciseItem()
        setContent(
            uiState =
                ExerciseListUiState(
                    filter = ExerciseStatusFilter.ACTIVE,
                    content = ExerciseListContent.Content(listOf(item)),
                ),
            onArchiveClicked = { archivedId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.exercise_list_row_menu_content_description))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_row_menu_archive))
            .performClick()

        assertEquals(item.id, archivedId)
    }

    @Test
    fun rowMenuShowsRestoreWhenViewingArchivedExercisesAndInvokesOnRestoreClicked() {
        var restoredId: ExerciseId? = null
        val item = exerciseItem()
        setContent(
            uiState =
                ExerciseListUiState(
                    filter = ExerciseStatusFilter.ARCHIVED,
                    content = ExerciseListContent.Content(listOf(item)),
                ),
            onRestoreClicked = { restoredId = it },
        )

        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.exercise_list_row_menu_content_description))
            .performClick()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_row_menu_restore))
            .performClick()

        assertEquals(item.id, restoredId)
    }

    @Test
    fun archivedMessageShowsSnackbarWithUndoAndInvokesOnUndoArchiveClicked() {
        var undoneId: ExerciseId? = null
        var shownMessageId: Long? = null
        val exerciseId = ExerciseId("1")
        setContent(
            uiState =
                ExerciseListUiState(
                    content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES),
                    messages = listOf(ExerciseListMessage.Archived(id = 1L, exerciseId = exerciseId)),
                ),
            onUndoArchiveClicked = { undoneId = it },
            onMessageShown = { shownMessageId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_message_archived))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_message_archived_undo))
            .performClick()

        assertEquals(exerciseId, undoneId)
        assertEquals(1L, shownMessageId)
    }

    @Test
    fun operationFailedMessageShowsSnackbarAndConsumesItWithoutUndo() {
        var undoInvoked = false
        var shownMessageId: Long? = null
        setContent(
            uiState =
                ExerciseListUiState(
                    content = ExerciseListContent.Empty(ExerciseListEmptyReason.NO_EXERCISES),
                    messages = listOf(ExerciseListMessage.OperationFailed(id = 7L)),
                ),
            onUndoArchiveClicked = { undoInvoked = true },
            onMessageShown = { shownMessageId = it },
        )

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.exercise_list_message_operation_failed))
            .assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS) { shownMessageId != null }

        assertEquals(false, undoInvoked)
        assertEquals(7L, shownMessageId)
    }

    private fun exerciseItem(id: String = "1") =
        ExerciseListItem(
            id = ExerciseId(id),
            name = "Bench Press",
            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
            defaultRestSeconds = 90,
            defaultLoadIncrementGrams = 2_500,
            planUsageCount = 0,
        )

    private companion object {
        const val SNACKBAR_AUTO_DISMISS_TIMEOUT_MILLIS = 8_000L
    }
}
