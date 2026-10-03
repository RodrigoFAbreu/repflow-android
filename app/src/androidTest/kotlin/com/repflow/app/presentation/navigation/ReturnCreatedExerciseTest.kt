package com.repflow.app.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Review O1 (J3, `D56`): the exercise editor's hand-back goes through a real
 * `NavController`. From the workout's picker the new id lands in the workout
 * entry's `SavedStateHandle`; from the plan editor it is a plain pop.
 */
@RunWith(AndroidJUnit4::class)
class ReturnCreatedExerciseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController
    private var workoutEntry: NavBackStackEntry? = null
    private var planEntry: NavBackStackEntry? = null

    private fun setHost() {
        composeRule.setContent { Host() }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Host() {
        navController = rememberNavController()
        NavHost(navController, startDestination = RepFlowDestinations.HOME) {
            composable(RepFlowDestinations.HOME) {}
            composable(RepFlowDestinations.WORKOUT_PATTERN) { workoutEntry = it }
            composable(RepFlowDestinations.PLAN_NEW) { planEntry = it }
            composable(RepFlowDestinations.EXERCISE_NEW_PATTERN) {}
        }
    }

    private fun onMain(block: () -> Unit) {
        composeRule.runOnUiThread(block)
        composeRule.waitForIdle()
    }

    @Test
    fun fromTheWorkoutThePreviousEntryReceivesTheIdAndTheEditorPops() {
        setHost()
        onMain { navController.navigate(RepFlowDestinations.WORKOUT) }
        onMain { navController.navigate(RepFlowDestinations.EXERCISE_NEW) }
        onMain { navController.returnCreatedExercise("x") }

        assertEquals(RepFlowDestinations.WORKOUT_PATTERN, navController.currentDestination?.route)
        assertEquals(
            "x",
            workoutEntry?.savedStateHandle?.get<String>(RepFlowDestinations.CREATED_EXERCISE_KEY),
        )
    }

    @Test
    fun fromThePlanEditorItIsAPlainPopWithNothingHandedBack() {
        setHost()
        onMain { navController.navigate(RepFlowDestinations.PLAN_NEW) }
        onMain { navController.navigate(RepFlowDestinations.EXERCISE_NEW) }
        onMain { navController.returnCreatedExercise("x") }

        assertEquals(RepFlowDestinations.PLAN_NEW, navController.currentDestination?.route)
        assertNull(planEntry?.savedStateHandle?.get<String>(RepFlowDestinations.CREATED_EXERCISE_KEY))
    }
}
