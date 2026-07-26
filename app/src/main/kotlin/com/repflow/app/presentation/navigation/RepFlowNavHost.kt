package com.repflow.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.repflow.app.presentation.exercise.editor.ExerciseEditorRoute
import com.repflow.app.presentation.exercise.list.ExerciseListRoute

/**
 * The app's single [NavHost] (D-1): the exercise list (start destination),
 * "create" and "edit" (see plan.md checkpoint 9). "Create" and "edit" share
 * one [ExerciseEditorRoute] destination each because the ViewModel itself
 * distinguishes the two modes from the presence of the `exerciseId`
 * `SavedStateHandle` argument (see [ExerciseEditorMode]).
 */
@Composable
fun RepFlowNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = RepFlowDestinations.EXERCISES) {
        composable(RepFlowDestinations.EXERCISES) {
            ExerciseListRoute(
                onExerciseClick = { id -> navController.navigate(RepFlowDestinations.exerciseEditRoute(id.value)) },
                onCreateClick = { navController.navigate(RepFlowDestinations.EXERCISE_NEW) },
            )
        }
        composable(RepFlowDestinations.EXERCISE_NEW) {
            ExerciseEditorRoute(
                onSaved = { navController.popBackStack() },
                onDismissed = { navController.popBackStack() },
            )
        }
        composable(
            route = RepFlowDestinations.EXERCISE_EDIT_PATTERN,
            arguments = listOf(navArgument(RepFlowDestinations.EXERCISE_EDIT_ARG) { type = NavType.StringType }),
        ) {
            ExerciseEditorRoute(
                onSaved = { navController.popBackStack() },
                onDismissed = { navController.popBackStack() },
            )
        }
    }
}
