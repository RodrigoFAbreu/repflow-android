package com.repflow.app.presentation.navigation

import com.repflow.app.R

/**
 * String route constants for [RepFlowNavHost] (D-1: plain string routes, no
 * type-safe routes, so no `kotlinx.serialization` dependency is pulled in).
 *
 * All routes below are registered in [RepFlowNavHost].
 */
object RepFlowDestinations {
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

    const val RECOVERY = "recovery"

    const val HISTORY = "history"

    const val BACKUP = "backup"

    /**
     * Single source of truth for the bottom [androidx.compose.material3.NavigationBar]'s
     * items (Milestone 8, CP1) - every top-level destination the user can
     * reach directly, in display order. Nested destinations (editors,
     * exercise/plan detail, history detail) are deliberately not listed
     * here; they navigate via [androidx.navigation.NavHostController.navigate]
     * from within a top-level screen instead.
     */
    val TOP_LEVEL_DESTINATIONS: List<TopLevelDestination> =
        listOf(
            TopLevelDestination(EXERCISES, R.string.exercise_list_title, R.string.exercise_list_content_description, "E"),
            TopLevelDestination(WORKOUT, R.string.workout_active_title, R.string.exercise_list_workout_content_description, "W"),
            TopLevelDestination(PLANS, R.string.training_plan_list_title, R.string.exercise_list_plans_content_description, "P"),
            TopLevelDestination(RECOVERY, R.string.recovery_futsal_title, R.string.exercise_list_recovery_content_description, "R"),
            TopLevelDestination(HISTORY, R.string.history_title, R.string.exercise_list_history_content_description, "H"),
            TopLevelDestination(BACKUP, R.string.backup_title, R.string.exercise_list_backup_content_description, "B"),
        )
}

/**
 * One entry in [RepFlowDestinations.TOP_LEVEL_DESTINATIONS]. [icon] is a
 * plain single-letter glyph, not a vector icon - neither `material-icons-core`
 * nor `material-icons-extended` is a dependency of this project (mirrors the
 * FAB's plain "+" glyph in `ExerciseListScreen`).
 */
data class TopLevelDestination(
    val route: String,
    val titleRes: Int,
    val contentDescriptionRes: Int,
    val icon: String,
)
