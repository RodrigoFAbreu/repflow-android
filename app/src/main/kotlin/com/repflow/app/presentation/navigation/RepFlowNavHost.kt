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
import com.repflow.app.presentation.trainingplan.editor.TrainingPlanEditorRoute
import com.repflow.app.presentation.trainingplan.list.TrainingPlanListRoute
import com.repflow.app.presentation.workout.ActiveWorkoutRoute

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
                onPlansClick = { navController.navigate(RepFlowDestinations.PLANS) },
                onWorkoutClick = { navController.navigate(RepFlowDestinations.WORKOUT) },
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
        composable(RepFlowDestinations.PLANS) {
            TrainingPlanListRoute(
                onPlanClick = { id -> navController.navigate(RepFlowDestinations.planEditRoute(id.value)) },
                onCreateClick = { navController.navigate(RepFlowDestinations.PLAN_NEW) },
            )
        }
        composable(RepFlowDestinations.PLAN_NEW) {
            TrainingPlanEditorRoute(
                onSaved = { navController.popBackStack() },
                onDismissed = { navController.popBackStack() },
            )
        }
        composable(
            route = RepFlowDestinations.PLAN_EDIT_PATTERN,
            arguments = listOf(navArgument(RepFlowDestinations.PLAN_EDIT_ARG) { type = NavType.StringType }),
        ) {
            TrainingPlanEditorRoute(
                onSaved = { navController.popBackStack() },
                onDismissed = { navController.popBackStack() },
            )
        }
        composable(RepFlowDestinations.WORKOUT) {
            ActiveWorkoutRoute()
        }
    }
}
