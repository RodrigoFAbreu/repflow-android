package com.repflow.app.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.repflow.app.presentation.backup.BackupRoute
import com.repflow.app.presentation.exercise.editor.ExerciseEditorRoute
import com.repflow.app.presentation.exercise.list.ExerciseListRoute
import com.repflow.app.presentation.history.HistoryRoute
import com.repflow.app.presentation.home.HomeRoute
import com.repflow.app.presentation.progress.ProgressPlaceholder
import com.repflow.app.presentation.progression.ProgressionRecommendationRoute
import com.repflow.app.presentation.recovery.RecoveryFutsalRoute
import com.repflow.app.presentation.recovery.RecoveryHistoryRoute
import com.repflow.app.presentation.settings.SettingsPlaceholder
import com.repflow.app.presentation.trainingplan.editor.TrainingPlanEditorRoute
import com.repflow.app.presentation.trainingplan.list.TrainingPlanListRoute
import com.repflow.app.presentation.workout.ActiveWorkoutRoute

private val TOP_LEVEL_ROUTES: Set<String> = RepFlowDestinations.TOP_LEVEL_DESTINATIONS.map { it.route }.toSet()

/**
 * The app's single [NavHost] (D-1), wrapped in a [Scaffold] that shows
 * [RepFlowBottomNavigationBar] only on the four top-level destinations
 * (Milestone 8, CP1; four since remediation-1 CP2) - every other route
 * renders without it. That is also what makes "workout mode replaces the
 * nav" hold: the workout route is not a tab, so the bar is absent there.
 *
 * Destinations that stopped being tabs are reached from inside the app, as
 * the design reaches them: the workout and recovery entry from Home, Settings
 * from Home, and the exercise library and backup from Settings.
 *
 * "Create" and "edit" share one [ExerciseEditorRoute] destination each because the ViewModel itself distinguishes the two
 * modes from the presence of the `exerciseId` `SavedStateHandle` argument
 * (see [ExerciseEditorMode]).
 */
@Composable
fun RepFlowNavHost(navController: NavHostController = rememberNavController()) {
    val currentRoute =
        navController
            .currentBackStackEntryAsState()
            .value
            ?.destination
            ?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TOP_LEVEL_ROUTES) {
                RepFlowBottomNavigationBar(
                    currentRoute = currentRoute,
                    onDestinationSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = RepFlowDestinations.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(RepFlowDestinations.HOME) {
                HomeRoute(
                    onOpenWorkout = { navController.navigate(RepFlowDestinations.WORKOUT) { launchSingleTop = true } },
                    onSettingsClick = { navController.navigate(RepFlowDestinations.SETTINGS) },
                    onLogRecoveryClick = { navController.navigate(RepFlowDestinations.RECOVERY) },
                    onCreatePlanClick = { navController.navigate(RepFlowDestinations.PLAN_NEW) },
                )
            }
            composable(RepFlowDestinations.SETTINGS) {
                SettingsPlaceholder(
                    onBackClick = { navController.popBackStack() },
                    onLibraryClick = { navController.navigate(RepFlowDestinations.EXERCISES) },
                    onBackupClick = { navController.navigate(RepFlowDestinations.BACKUP) },
                )
            }
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
                ActiveWorkoutRoute(
                    onOpenRecommendation = { id -> navController.navigate(RepFlowDestinations.progressionRoute(id.value)) },
                )
            }
            composable(
                route = RepFlowDestinations.PROGRESSION_PATTERN,
                arguments = listOf(navArgument(RepFlowDestinations.PROGRESSION_EXERCISE_ARG) { type = NavType.StringType }),
            ) {
                ProgressionRecommendationRoute(onBack = { navController.popBackStack() })
            }
            composable(RepFlowDestinations.RECOVERY) {
                RecoveryFutsalRoute(onHistoryClick = { navController.navigate(RepFlowDestinations.RECOVERY_HISTORY) })
            }
            composable(RepFlowDestinations.RECOVERY_HISTORY) {
                RecoveryHistoryRoute(onBackClick = { navController.popBackStack() })
            }
            composable(RepFlowDestinations.HISTORY) {
                HistoryRoute()
            }
            composable(RepFlowDestinations.PROGRESS) {
                ProgressPlaceholder()
            }
            composable(RepFlowDestinations.BACKUP) {
                BackupRoute()
            }
        }
    }
}
