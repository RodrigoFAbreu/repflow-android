package com.repflow.app.presentation.navigation

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
     * The progression recommendation for one exercise (remediation-1 CP6),
     * reached from the workout picker row's `Why ›` and focus mode's suggestion
     * strip (CP8); CP9's finish screen adds its own way in.
     */
    const val PROGRESSION_EXERCISE_ARG = "exerciseId"
    const val PROGRESSION_PATTERN = "progression/{$PROGRESSION_EXERCISE_ARG}"

    fun progressionRoute(exerciseId: String): String = "progression/$exerciseId"

    const val RECOVERY = "recovery"
    const val RECOVERY_HISTORY = "recovery/history"

    const val HISTORY = "history"

    const val PROGRESS = "progress"

    const val SETTINGS = "settings"

    const val BACKUP = "backup"

    /**
     * Single source of truth for the bottom [androidx.compose.material3.NavigationBar]'s
     * items - the design's four top-level destinations (artboard `4a`,
     * `nTabs`), in display order: Home, Plans, History, Progress.
     *
     * Everything else is reached from inside one of these, never from the bar
     * (`6b`: "Bottom nav on the four top-level destinations only"): the
     * workout and recovery entry from Home, Settings from Home (its header
     * gear once CP5 builds Home), and the exercise library and backup from
     * Settings. Because [RepFlowNavHost]
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
