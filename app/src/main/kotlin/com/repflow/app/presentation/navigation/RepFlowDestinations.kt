package com.repflow.app.presentation.navigation

/**
 * String route constants for [RepFlowNavHost] (D-1: plain string routes, no
 * type-safe routes, so no `kotlinx.serialization` dependency is pulled in).
 *
 * `EXERCISE_NEW` and the `EXERCISE_EDIT_*` route pieces are not yet
 * registered in [RepFlowNavHost] - they are wired once the editor
 * destination exists (see plan.md checkpoint 9).
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
}
