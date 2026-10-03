package com.repflow.app.presentation.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/**
 * String route constants for [RepFlowNavHost] (D-1: plain string routes, no
 * type-safe routes, so no `kotlinx.serialization` dependency is pulled in).
 *
 * All routes below are registered in [RepFlowNavHost].
 */
object RepFlowDestinations {
    /** The start destination: the first of the four bottom-nav tabs. */
    const val HOME = "home"

    const val EXERCISES = "exercises"
    const val EXERCISE_NEW = "exercises/new"

    /**
     * The create route's optional name prefill - the library's `Create
     * "<query>"` (remediation-1 CP10). Absent by default, so the bare
     * [EXERCISE_NEW] still resolves; it never decides the editor's mode,
     * which stays derived from [EXERCISE_EDIT_ARG] alone.
     */
    const val EXERCISE_NEW_NAME_ARG = "name"
    const val EXERCISE_NEW_PATTERN = "$EXERCISE_NEW?$EXERCISE_NEW_NAME_ARG={$EXERCISE_NEW_NAME_ARG}"

    /** [EXERCISE_NEW] with [prefillName] as the new exercise's name, URI-encoded. */
    fun exerciseNewRoute(prefillName: String): String = "$EXERCISE_NEW?$EXERCISE_NEW_NAME_ARG=${Uri.encode(prefillName)}"

    const val EXERCISE_EDIT_ARG = "exerciseId"
    const val EXERCISE_EDIT_PATTERN = "exercises/{$EXERCISE_EDIT_ARG}"

    fun exerciseEditRoute(exerciseId: String): String = "exercises/$exerciseId"

    const val PLANS = "plans"
    const val PLAN_NEW = "plans/new"
    const val PLAN_EDIT_ARG = "planId"
    const val PLAN_EDIT_PATTERN = "plans/{$PLAN_EDIT_ARG}"

    fun planEditRoute(planId: String): String = "plans/$planId"

    const val WORKOUT = "workout"

    /**
     * The workout surface opened with its finish sheet already raised - Home's
     * `Finish it` (remediation-1 CP9). [WORKOUT] itself still navigates here,
     * with the flag off.
     */
    const val WORKOUT_FINISH_ARG = "finish"
    const val WORKOUT_PATTERN = "$WORKOUT?$WORKOUT_FINISH_ARG={$WORKOUT_FINISH_ARG}"
    const val WORKOUT_WITH_FINISH_SHEET = "$WORKOUT?$WORKOUT_FINISH_ARG=true"

    /**
     * The key under which the exercise editor hands a just-created exercise's id
     * back to the workout entry that opened it (`D56`).
     */
    const val CREATED_EXERCISE_KEY = "created_exercise_id"

    /** The done screen for one finished session (remediation-1 CP9, `4a` `nDone`). */
    const val WORKOUT_DONE_ARG = "sessionId"
    const val WORKOUT_DONE_PATTERN = "workout/done/{$WORKOUT_DONE_ARG}"

    fun workoutDoneRoute(sessionId: String): String = "workout/done/$sessionId"

    /**
     * The progression recommendation for one exercise (remediation-1 CP6),
     * reached from the workout picker row's `Why ›` and focus mode's suggestion
     * strip (CP8), and from the done screen's recommendations (CP9).
     */
    const val PROGRESSION_EXERCISE_ARG = "exerciseId"
    const val PROGRESSION_PATTERN = "progression/{$PROGRESSION_EXERCISE_ARG}"

    fun progressionRoute(exerciseId: String): String = "progression/$exerciseId"

    const val RECOVERY = "recovery"
    const val RECOVERY_HISTORY = "recovery/history"

    const val HISTORY = "history"

    const val PROGRESS = "progress"

    /** Settings, reached from Home; its Data group carries backup and restore (remediation-1 CP14). */
    const val SETTINGS = "settings"

    /** Archived exercises and plans with `Restore`, reached only from Settings (remediation-1-remediation-1 CP7). */
    const val ARCHIVED = "settings/archived"

    /**
     * Single source of truth for the bottom [androidx.compose.material3.NavigationBar]'s
     * items - the design's four top-level destinations (artboard `4a`,
     * `nTabs`), in display order: Home, Plans, History, Progress.
     *
     * Everything else is reached from inside one of these, never from the bar
     * (`6b`: "Bottom nav on the four top-level destinations only"): the
     * workout and recovery entry from Home, Settings from Home (its header
     * gear once CP5 builds Home), and the exercise library from Settings,
     * whose Data group holds backup and restore. Because [RepFlowNavHost]
     * shows the bar only on these routes, every other route - the workout
     * route included, which is what "workout mode replaces the nav" asks for -
     * renders without it.
     */
    val TOP_LEVEL_DESTINATIONS: List<TopLevelDestination> =
        listOf(
            TopLevelDestination(
                route = HOME,
                titleRes = R.string.nav_home_title,
                contentDescriptionRes = R.string.nav_home_content_description,
                icon = RepFlowIcons.Nav.home,
                selectedIcon = RepFlowIcons.Nav.homeSelected,
            ),
            TopLevelDestination(
                route = PLANS,
                titleRes = R.string.training_plan_list_title,
                contentDescriptionRes = R.string.exercise_list_plans_content_description,
                icon = RepFlowIcons.Nav.plans,
                selectedIcon = RepFlowIcons.Nav.plansSelected,
            ),
            TopLevelDestination(
                route = HISTORY,
                titleRes = R.string.history_title,
                contentDescriptionRes = R.string.exercise_list_history_content_description,
                icon = RepFlowIcons.Nav.history,
                selectedIcon = RepFlowIcons.Nav.historySelected,
            ),
            TopLevelDestination(
                route = PROGRESS,
                titleRes = R.string.nav_progress_title,
                contentDescriptionRes = R.string.nav_progress_content_description,
                icon = RepFlowIcons.Nav.progress,
                selectedIcon = RepFlowIcons.Nav.progressSelected,
            ),
        )
}

/**
 * One entry in [RepFlowDestinations.TOP_LEVEL_DESTINATIONS]. [icon] (drawn
 * when the tab is not selected) and [selectedIcon] (drawn when it is) are
 * drawable resource ids from the bounded local Phosphor set
 * ([RepFlowIcons.Nav]) - plain `Int`s, so this stays ordinary data a
 * non-composable holder can carry. The pair is the design's regular/fill
 * treatment, which is what keeps selection from being signalled by colour
 * alone. Neither `material-icons-core` nor `material-icons-extended` is a
 * dependency of this project.
 */
data class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val contentDescriptionRes: Int,
    @param:DrawableRes @get:DrawableRes val icon: Int,
    @param:DrawableRes @get:DrawableRes val selectedIcon: Int,
)
