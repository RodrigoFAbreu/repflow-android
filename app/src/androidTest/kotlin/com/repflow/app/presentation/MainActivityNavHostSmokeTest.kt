package com.repflow.app.presentation

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.repflow.app.R
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end instrumentation smoke test: boots [MainActivity] through Hilt
 * and walks the app's information architecture - the design's four tabs
 * (Home, Plans, History, Progress) with `HOME` as the start destination
 * (remediation-1 CP2).
 *
 * Every destination that stopped being a tab is reached here by its new
 * inward path, **walked from the start destination** rather than opened from
 * the middle of the chain: Workout, Recovery and Settings from Home, and the
 * exercise library and the backup actions from Settings. An assertion that started at a
 * relocated route would pass against a route no user can open.
 *
 * The per-destination walks also keep this file's original purpose: a
 * registered `NavHost` route is not proof a screen can actually be
 * constructed, because `hiltViewModel()` fails at runtime (not compile time)
 * if the target ViewModel is missing `@HiltViewModel`.
 */
class MainActivityNavHostSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tabDescriptions =
        listOf(
            R.string.nav_home_content_description,
            R.string.exercise_list_plans_content_description,
            R.string.exercise_list_history_content_description,
            R.string.nav_progress_content_description,
        )

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun clickByDescription(
        @StringRes id: Int,
    ) {
        composeRule.onNodeWithContentDescription(string(id)).performClick()
    }

    private fun clickByText(
        @StringRes id: Int,
    ) {
        composeRule.onNodeWithText(string(id)).performClick()
    }

    /** Home's cards are drawn from Room flows, which the Compose idling check does not wait for. */
    private fun waitForText(
        @StringRes id: Int,
    ) {
        val text = string(id)
        composeRule.waitUntil(WAIT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun assertBottomNavAbsent() {
        tabDescriptions.forEach { composeRule.onNodeWithContentDescription(string(it)).assertDoesNotExist() }
    }

    @Test
    fun homeIsTheStartDestinationAndTheBarCarriesTheFourTabs() {
        composeRule.onNodeWithText(string(R.string.home_greeting)).assertIsDisplayed()
        tabDescriptions.forEach { composeRule.onNodeWithContentDescription(string(it)).assertIsDisplayed() }
    }

    /**
     * The workout surface is reachable from Home and renders without the
     * bottom nav ("workout mode replaces the nav"). The two halves are
     * asserted together on purpose: the nav gate is a property of the route,
     * so on its own it would also pass on a route no user can reach.
     *
     * Like the History walk below, this assumes a fresh install: with no plan
     * yet, Home's start card is `1d`'s first-run card, whose `Empty workout`
     * starts a session and opens the workout board (remediation-1 CP5, CP7).
     * The session is abandoned again afterwards - through the board's own way
     * out, the `X`, the leave sheet and the abandon confirmation (CP7) - so
     * the next walk finds Home as this one did.
     */
    @Test
    fun workoutIsReachableFromHomeAndReplacesTheNav() {
        waitForText(R.string.home_empty_workout)
        clickByText(R.string.home_empty_workout)
        waitForText(R.string.workout_board_empty_body)
        composeRule.onNodeWithText(string(R.string.workout_board_empty_body)).assertIsDisplayed()
        assertBottomNavAbsent()
        clickByDescription(R.string.workout_board_leave_content_description)
        waitForText(R.string.workout_leave_abandon)
        clickByText(R.string.workout_leave_abandon)
        waitForText(R.string.workout_abandon_confirm_action)
        clickByText(R.string.workout_abandon_confirm_action)
        waitForText(R.string.home_greeting)
    }

    /**
     * Remediation-1 CP13: `3c` has no `Recovery entry` heading any more, so the
     * screen is recognised by its pinned `Save entry`, which no other
     * destination carries.
     */
    @Test
    fun recoveryIsReachableFromHome() {
        clickByDescription(R.string.home_recovery_log_content_description)
        composeRule
            .onNodeWithText(string(R.string.recovery_futsal_save_entry))
            .assertIsDisplayed()
    }

    @Test
    fun recoveryHistoryIsReachableBehindRecovery() {
        clickByDescription(R.string.home_recovery_log_content_description)
        clickByDescription(R.string.recovery_futsal_view_history_content_description)
        composeRule.onNodeWithText(string(R.string.recovery_history_title)).assertIsDisplayed()
    }

    /** Settings is not a tab, so it shows no nav either (`4a`: `nShowNav = nTab !== 'settings'`). */
    @Test
    fun settingsIsReachableFromHomeWithoutTheNav() {
        clickByDescription(R.string.home_settings_content_description)
        composeRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        assertBottomNavAbsent()
    }

    @Test
    fun exerciseLibraryIsReachableFromSettings() {
        clickByDescription(R.string.home_settings_content_description)
        clickByText(R.string.settings_library_row)
        // The search field only exists on the exercise list screen.
        composeRule.onNodeWithText(string(R.string.exercise_list_search_hint)).assertIsDisplayed()
    }

    /** Since remediation-1 CP14 backup and restore are Settings' own Data rows (`4a`), not a screen of their own. */
    @Test
    fun backupIsReachableFromSettings() {
        clickByDescription(R.string.home_settings_content_description)
        composeRule.onNodeWithText(string(R.string.backup_export_action)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.backup_restore_action)).performScrollTo().assertIsDisplayed()
    }

    /**
     * Remediation-1-remediation-1 CP7: the Archived screen is reached only from
     * Settings and returns there. Like the other walks this assumes a fresh
     * install, where nothing is archived and one empty state shows.
     */
    @Test
    fun archivedIsReachableFromSettingsAndBackReturnsThere() {
        clickByDescription(R.string.home_settings_content_description)
        composeRule.onNodeWithText(string(R.string.settings_archived_row)).performScrollTo().performClick()
        waitForText(R.string.archived_empty_both)
        composeRule.onNodeWithText(string(R.string.archived_title)).assertIsDisplayed()
        assertBottomNavAbsent()

        clickByDescription(R.string.archived_back_content_description)

        composeRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
    }

    @Test
    fun historyTabOpensWithoutCrashing() {
        clickByDescription(R.string.exercise_list_history_content_description)
        composeRule.onNodeWithText(string(R.string.history_empty)).assertIsDisplayed()
    }

    /** Since remediation-1 CP15 the tab is the real Progress screen; with no history it shows its empty state, read from Room. */
    @Test
    fun progressTabOpensWithoutCrashing() {
        clickByDescription(R.string.nav_progress_content_description)
        waitForText(R.string.progress_empty)
        composeRule.onNodeWithText(string(R.string.progress_empty)).assertIsDisplayed()
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
    }
}
