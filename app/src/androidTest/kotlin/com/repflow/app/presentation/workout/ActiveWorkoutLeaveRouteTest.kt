package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.history.ObserveRecentTraining
import com.repflow.app.application.history.ObserveWorkoutSummary
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.RecordWorkoutSetCommand
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.progression.LocalProgressionRecommendationRepository
import com.repflow.app.data.recovery.LocalFutsalRepository
import com.repflow.app.data.recovery.LocalRecoveryRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.home.HomeRoute
import com.repflow.app.presentation.home.HomeViewModel
import com.repflow.app.presentation.navigation.RepFlowDestinations
import com.repflow.app.presentation.navigation.leaveWorkoutForHome
import com.repflow.app.presentation.navigation.openWorkoutDone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Workout mode's ways out (remediation-1 plan CP7 item 1, CP9 item 1), over a
 * real in-memory Room database and a nav graph that registers `HOME`, the
 * workout and the done screen exactly as `RepFlowNavHost` does - the same
 * `leaveWorkoutForHome` / `openWorkoutDone` stack removal and the workout's
 * `finish` argument, the real `HomeRoute`, `ActiveWorkoutRoute` and
 * `WorkoutDoneRoute`, ViewModels built over the test database in place of
 * Hilt's. Each walk starts at Home and opens the workout through Home's own
 * `Resume` or `Finish it`, so nothing is asserted against a route no user can
 * reach.
 *
 * The four assertions the plan owes:
 * - `Leave it running and go Home` lands on Home with the session still
 *   active and the resume card showing, and leaves no board entry behind, so
 *   Back from Home cannot return to the board;
 * - system back on the board opens the leave sheet instead of popping;
 * - `Abandon this workout` abandons nothing until its destructive
 *   confirmation is confirmed;
 * - a confirmed abandon persists `ABANDONED` with every logged set kept
 *   (`LocalWorkoutRepository`'s retention, which the in-memory fakes do not
 *   exercise).
 *
 * And the two CP9 owes:
 * - Home's `Finish it` opens the finish sheet rather than completing the
 *   session, and dismissing it returns to Home with the session still active
 *   and no board entry on the back stack (`D16`);
 * - the sheet's confirm completes the session and lands on the done screen,
 *   with no board entry behind it, and `Back to Home` goes Home.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutLeaveRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock { Instant.now() }
    private val exerciseId = ExerciseId("exercise-squat")
    private lateinit var database: RepFlowDatabase
    private lateinit var workouts: LocalWorkoutRepository
    private lateinit var navController: NavHostController
    private var nextId = 0
    private val ids = IdentifierGenerator { "id-${++nextId}" }

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        workouts = LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao())
        runBlocking { success(LocalExerciseRepository(database.exerciseDao()).insert(exercise())) }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun leaveItRunningLandsOnHomeWithTheSessionActiveAndNoBoardOnTheBackStack() {
        val sessionId = startSession()
        openWorkoutFromHome()

        clickDescription(R.string.workout_board_leave_content_description)
        waitForText(string(R.string.workout_leave_go_home))
        composeRule.onNodeWithText(string(R.string.workout_leave_go_home)).performClick()

        waitForText(string(R.string.home_resume))
        composeRule.onNodeWithText(string(R.string.home_resume)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_resume_title, string(R.string.home_untitled_workout))).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(RepFlowDestinations.HOME, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
            assertFalse(
                "Back from Home must not return to the board",
                navController.currentBackStack.value.any { it.destination.route == RepFlowDestinations.WORKOUT_PATTERN },
            )
        }
        assertEquals(WorkoutSessionStatus.ACTIVE, status(sessionId))
    }

    @Test
    fun systemBackOnTheBoardOpensTheLeaveSheetInsteadOfPopping() {
        startSession()
        openWorkoutFromHome()

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        waitForText(string(R.string.workout_leave_title))
        composeRule.onNodeWithText(string(R.string.workout_leave_title)).assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(RepFlowDestinations.WORKOUT_PATTERN, navController.currentDestination?.route) }
    }

    @Test
    fun abandonThisWorkoutAbandonsNothingUntilItsConfirmationIsConfirmed() {
        val sessionId = startSession()
        openWorkoutFromHome()

        openAbandonConfirmation()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_title)).assertIsDisplayed()
        assertEquals(WorkoutSessionStatus.ACTIVE, status(sessionId))

        composeRule.onNodeWithText(string(R.string.workout_abandon_keep_action)).performClick()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_title)).assertDoesNotExist()
        assertEquals(WorkoutSessionStatus.ACTIVE, status(sessionId))

        openAbandonConfirmation()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_action)).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { status(sessionId) == WorkoutSessionStatus.ABANDONED }
        waitForText(string(R.string.home_greeting))
        composeRule.runOnIdle { assertEquals(RepFlowDestinations.HOME, navController.currentDestination?.route) }
    }

    @Test
    fun aConfirmedAbandonIsStoredAbandonedWithEveryLoggedSetKept() {
        val sessionId = startSession()
        runBlocking {
            val workoutExerciseId =
                success(
                    AddWorkoutExercise(workouts, ids)(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = exerciseId,
                            exerciseNameSnapshot = "Back Squat",
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            plannedExerciseId = null,
                        ),
                    ),
                )
            listOf(100.0 to 5, 105.0 to 4).forEach { (load, reps) ->
                success(
                    RecordWorkoutSet(workouts, clock, ids)(
                        RecordWorkoutSetCommand(
                            sessionId = sessionId,
                            exerciseId = workoutExerciseId,
                            load = load,
                            reps = reps,
                            durationSeconds = null,
                            rpe = null,
                            isWarmup = false,
                        ),
                    ),
                )
            }
        }
        openWorkoutFromHome()

        openAbandonConfirmation()
        composeRule.onNodeWithText(string(R.string.workout_abandon_confirm_action)).performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) { status(sessionId) == WorkoutSessionStatus.ABANDONED }

        val stored = runBlocking { checkNotNull(workouts.findById(sessionId)) }
        assertEquals(WorkoutSessionStatus.ABANDONED, stored.status)
        val sets = stored.exercises.single().sets
        assertEquals(listOf(100.0 to 5, 105.0 to 4), sets.map { it.load to it.reps })
    }

    @Test
    fun finishItFromHomeOpensTheFinishSheetAndDismissingItReturnsHomeWithNoBoardBehind() {
        val sessionId = startSession()
        renderFromHome()

        composeRule.onNodeWithText(string(R.string.home_finish_it)).performClick()

        waitForText(string(R.string.workout_finish_title))
        composeRule.onNodeWithText(string(R.string.workout_finish_title)).assertIsDisplayed()
        assertEquals("Finish it must not complete the session by itself", WorkoutSessionStatus.ACTIVE, status(sessionId))

        // Dismissing the sheet raised from Home: its own `Keep training`, which does what a scrim tap or back does.
        composeRule.onNodeWithText(string(R.string.workout_finish_keep_training)).performClick()

        waitForText(string(R.string.home_resume))
        composeRule.onNodeWithText(string(R.string.home_resume)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(RepFlowDestinations.HOME, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
            assertFalse(
                "Back from Home must not return to the board",
                navController.currentBackStack.value.any { it.destination.route == RepFlowDestinations.WORKOUT_PATTERN },
            )
        }
        assertEquals(WorkoutSessionStatus.ACTIVE, status(sessionId))
    }

    @Test
    fun finishAndSaveCompletesTheSessionAndLandsOnTheDoneScreenWithNoBoardBehind() {
        val sessionId = startSession()
        openWorkoutFromHome()

        composeRule.onNodeWithText(string(R.string.workout_board_finish)).performClick()
        waitForText(string(R.string.workout_finish_confirm))
        composeRule.onNodeWithText(string(R.string.workout_finish_confirm)).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { status(sessionId) == WorkoutSessionStatus.COMPLETED }
        waitForText(string(R.string.workout_done_back_home))
        composeRule.onNodeWithText(string(R.string.workout_done_versus_label).uppercase()).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(RepFlowDestinations.WORKOUT_DONE_PATTERN, navController.currentDestination?.route)
            assertEquals(RepFlowDestinations.HOME, navController.previousBackStackEntry?.destination?.route)
            assertFalse(navController.currentBackStack.value.any { it.destination.route == RepFlowDestinations.WORKOUT_PATTERN })
        }

        composeRule.onNodeWithText(string(R.string.workout_done_back_home)).performClick()

        waitForText(string(R.string.home_greeting))
        composeRule.runOnIdle {
            assertEquals(RepFlowDestinations.HOME, navController.currentDestination?.route)
            assertNull(navController.previousBackStackEntry)
        }
    }

    private fun startSession(): WorkoutSessionId =
        runBlocking {
            success(StartWorkoutSession(workouts, clock, ids)(StartWorkoutSessionCommand(null)))
        }

    private fun status(sessionId: WorkoutSessionId): WorkoutSessionStatus? = runBlocking { workouts.findById(sessionId)?.status }

    /** Renders the graph at Home and walks into the workout by Home's own `Resume`. */
    private fun openWorkoutFromHome() {
        renderFromHome()
        composeRule.onNodeWithText(string(R.string.home_resume)).performClick()
        val leave = string(R.string.workout_board_leave_content_description)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescriptionExactly(leave)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Renders `HOME`, the workout and the done screen as `RepFlowNavHost` registers them, and waits for Home's resume card. */
    private fun renderFromHome() {
        val homeViewModel = homeViewModel()
        val workoutViewModel = workoutViewModel()
        composeRule.setContent {
            RepFlowTheme {
                val controller = rememberNavController()
                navController = controller
                NavHost(navController = controller, startDestination = RepFlowDestinations.HOME) {
                    composable(RepFlowDestinations.HOME) {
                        HomeRoute(
                            onOpenWorkout = { controller.navigate(RepFlowDestinations.WORKOUT) { launchSingleTop = true } },
                            onFinishWorkout = {
                                controller.navigate(RepFlowDestinations.WORKOUT_WITH_FINISH_SHEET) { launchSingleTop = true }
                            },
                            onSettingsClick = {},
                            onLogRecoveryClick = {},
                            onCreatePlanClick = {},
                            viewModel = homeViewModel,
                        )
                    }
                    composable(
                        route = RepFlowDestinations.WORKOUT_PATTERN,
                        arguments =
                            listOf(
                                navArgument(RepFlowDestinations.WORKOUT_FINISH_ARG) {
                                    type = NavType.BoolType
                                    defaultValue = false
                                },
                            ),
                    ) { entry ->
                        ActiveWorkoutRoute(
                            onOpenRecommendation = {},
                            onCreateExercise = {},
                            onLeaveWorkout = { controller.leaveWorkoutForHome() },
                            onWorkoutFinished = { id -> controller.openWorkoutDone(id.value) },
                            raiseFinishFromHome = entry.arguments?.getBoolean(RepFlowDestinations.WORKOUT_FINISH_ARG) == true,
                            viewModel = workoutViewModel,
                        )
                    }
                    composable(
                        route = RepFlowDestinations.WORKOUT_DONE_PATTERN,
                        arguments = listOf(navArgument(RepFlowDestinations.WORKOUT_DONE_ARG) { type = NavType.StringType }),
                    ) { entry ->
                        val doneViewModel =
                            remember(
                                entry,
                            ) { doneViewModel(checkNotNull(entry.arguments?.getString(RepFlowDestinations.WORKOUT_DONE_ARG))) }
                        WorkoutDoneRoute(
                            onOpenRecommendation = {},
                            onBackToHome = { controller.leaveWorkoutForHome() },
                            viewModel = doneViewModel,
                        )
                    }
                }
            }
        }
        waitForText(string(R.string.home_resume))
    }

    private fun openAbandonConfirmation() {
        clickDescription(R.string.workout_board_leave_content_description)
        waitForText(string(R.string.workout_leave_abandon))
        composeRule.onNodeWithText(string(R.string.workout_leave_abandon)).performClick()
        waitForText(string(R.string.workout_abandon_confirm_title))
    }

    private fun clickDescription(
        @StringRes id: Int,
    ) {
        composeRule.onNodeWithContentDescription(string(id)).performClick()
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun plans(): LocalTrainingPlanRepository =
        LocalTrainingPlanRepository(
            database,
            database.trainingPlanDao(),
            database.trainingPlanVersionDao(),
            database.plannedExerciseDao(),
        )

    private fun homeViewModel(): HomeViewModel {
        val plans = plans()
        return HomeViewModel(
            clock = clock,
            observeReadiness = ObserveReadiness(LocalRecoveryRepository(database.recoveryEntryDao())),
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workouts),
            observeTrainingPlans = ObserveTrainingPlans(plans),
            observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(plans),
            observeRecentTraining = ObserveRecentTraining(workouts),
            startWorkoutSession = StartWorkoutSession(workouts, clock, ids),
            startWorkoutSessionFromPlan =
                StartWorkoutSessionFromPlan(workouts, GetExercise(LocalExerciseRepository(database.exerciseDao())), clock, ids),
            abandonWorkoutSession = AbandonWorkoutSession(workouts, clock),
        )
    }

    private fun doneViewModel(sessionId: String): WorkoutDoneViewModel =
        WorkoutDoneViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RepFlowDestinations.WORKOUT_DONE_ARG to sessionId)),
            observeWorkoutSummary = ObserveWorkoutSummary(workouts),
            observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(plans()),
            progressionRecommendationRepository = LocalProgressionRecommendationRepository(database.progressionRecommendationDao()),
        )

    private fun workoutViewModel(): ActiveWorkoutViewModel {
        val plans = plans()
        val exercises = LocalExerciseRepository(database.exerciseDao())
        val recommendations = LocalProgressionRecommendationRepository(database.progressionRecommendationDao())
        val dayContext =
            GetWorkoutDayContext(
                LocalRecoveryRepository(database.recoveryEntryDao()),
                LocalFutsalRepository(database.futsalSessionDao()),
                clock,
            )
        return ActiveWorkoutViewModel(
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workouts),
            observeExercises = ObserveExercises(exercises),
            observeTrainingPlans = ObserveTrainingPlans(plans),
            trainingPlanRepository = plans,
            getWorkoutDayContext = dayContext,
            progressionRecommendationRepository = recommendations,
            startWorkoutSession = StartWorkoutSession(workouts, clock, ids),
            startWorkoutSessionFromPlan = StartWorkoutSessionFromPlan(workouts, GetExercise(exercises), clock, ids),
            addWorkoutExercise = AddWorkoutExercise(workouts, ids),
            recordWorkoutSet = RecordWorkoutSet(workouts, clock, ids),
            undoLastWorkoutSet = UndoLastWorkoutSet(workouts),
            editLastWorkoutSet = EditLastWorkoutSet(workouts, clock),
            startRestTimer = StartRestTimer(workouts, clock),
            adjustRestTimer = AdjustRestTimer(workouts, clock),
            skipRestTimer = SkipRestTimer(workouts),
            completeWorkoutSession =
                CompleteWorkoutSession(
                    workouts,
                    plans,
                    ComputeProgressionRecommendation(recommendations, dayContext, clock, ids),
                    clock,
                ),
            abandonWorkoutSession = AbandonWorkoutSession(workouts, clock),
        )
    }

    private fun exercise(): Exercise =
        success(
            Exercise.create(
                id = exerciseId,
                name = success(ExerciseName.create("Back Squat")),
                trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                instructions = null,
                defaultLoadIncrement = null,
                defaultRestDuration = null,
                origin = ExerciseOrigin.CUSTOM,
                createdAt = Instant.parse("2026-08-01T09:00:00Z"),
            ),
        )

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
