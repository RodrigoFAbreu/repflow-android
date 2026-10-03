package com.repflow.app.presentation.progression

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
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
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
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.progression.RecordManualOverride
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditLastWorkoutSet
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
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
import com.repflow.app.data.settings.LocalSettingsRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.progression.ProgressionRecommendation
import com.repflow.app.domain.progression.ProgressionRecommendationId
import com.repflow.app.domain.progression.ProgressionResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.presentation.RepFlowTheme
import com.repflow.app.presentation.navigation.RepFlowDestinations
import com.repflow.app.presentation.workout.ActiveWorkoutRoute
import com.repflow.app.presentation.workout.ActiveWorkoutViewModel
import com.repflow.app.presentation.workout.RestNotificationCanceller
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The recommendation screen over a real in-memory Room database
 * (remediation-1 CP6), for the two assertions plan CP6 owes that a stateless
 * test cannot make:
 *
 * - **an override reaches `RecordManualOverride`** - the stored
 *   recommendation carries the user's choice afterwards, its own result
 *   untouched - **and the screen then re-renders as overridden**;
 * - **the inward path**: from the workout's exercise picker, the
 *   recommendation row's `Why ›` reaches this screen through the nav graph,
 *   with the route's own pattern and argument - and, since remediation-1 CP8,
 *   so does focus mode's suggestion strip. A test that only rendered the
 *   screen would pass with every way in missing (the CP2 item 7 discipline).
 *   The graph here registers the two destinations exactly as `RepFlowNavHost`
 *   does, with ViewModels built over the test database in place of Hilt's.
 */
@RunWith(AndroidJUnit4::class)
class ProgressionRecommendationRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val computedAt = Instant.parse("2026-08-09T18:00:00Z")
    private val clock = Clock { Instant.parse("2026-08-11T09:00:00Z") }
    private val exerciseId = ExerciseId("exercise-squat")
    private lateinit var database: RepFlowDatabase
    private lateinit var recommendations: LocalProgressionRecommendationRepository
    private var nextId = 0
    private val ids = IdentifierGenerator { "id-${++nextId}" }

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        recommendations = LocalProgressionRecommendationRepository(database.progressionRecommendationDao())
        runBlocking {
            success(LocalExerciseRepository(database.exerciseDao()).insert(exercise()))
            success(
                recommendations.insert(
                    success(
                        ProgressionRecommendation.create(
                            id = ProgressionRecommendationId("rec-1"),
                            exerciseId = exerciseId,
                            result = ProgressionResult.IncreaseLoad,
                            reasons = listOf("Every working set reached 8+ reps at average RPE 7.0"),
                            policyVersion = 1,
                            computedAt = computedAt,
                        ),
                    ),
                ),
            )
        }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun anOverrideReachesRecordManualOverrideAndTheScreenRerendersAsOverridden() {
        composeRule.setContent {
            RepFlowTheme {
                ProgressionRecommendationRoute(onBack = {}, viewModel = recommendationViewModel(exerciseId.value))
            }
        }
        waitForText(string(R.string.progression_pick_another))
        composeRule.onNodeWithText(string(R.string.progression_result_increase_load)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.progression_pick_another)).performClick()
        composeRule.onNodeWithText(string(R.string.progression_result_reduce_load)).performClick()

        waitForText(string(R.string.progression_overridden_title))
        composeRule.onNodeWithText(string(R.string.progression_overridden_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.progression_record_chosen, string(R.string.progression_result_reduce_load)))
            .performScrollTo()
            .assertIsDisplayed()
        val stored = runBlocking { checkNotNull(recommendations.findLatestForExercise(exerciseId)) }
        assertEquals(ProgressionResult.IncreaseLoad, stored.result)
        assertEquals(ProgressionResult.ReduceLoad, stored.manualOverride?.result)
    }

    @Test
    fun theWorkoutPickersWhyReachesTheRecommendationScreen() {
        runBlocking { success(StartWorkoutSession(workoutRepository(), clock, ids)(StartWorkoutSessionCommand(null))) }
        setWorkoutGraph()
        val why = string(R.string.progression_why_content_description, "Back Squat")
        waitForText(string(R.string.workout_active_add_exercise))
        composeRule.onNodeWithText(string(R.string.workout_active_add_exercise)).performScrollTo().performClick()
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescriptionExactly(why)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(why).performClick()

        assertOnTheRecommendationScreen()
    }

    /**
     * Plan CP8 item 5's inward-path assertion (remediation-1 CP8): focus mode's
     * suggestion strip reaches the recommendation screen through the nav graph.
     * The exercise is on the workout, the board opens its focus mode, and the
     * strip's `Why ›` - not the picker's - is the way in.
     */
    @Test
    fun theFocusModeSuggestionStripsWhyReachesTheRecommendationScreen() {
        runBlocking {
            val workouts = workoutRepository()
            val sessionId = success(StartWorkoutSession(workouts, clock, ids)(StartWorkoutSessionCommand(null)))
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
        }
        setWorkoutGraph()
        waitForText("Back Squat")
        composeRule.onNodeWithText("Back Squat").performClick()
        waitForText(string(R.string.workout_focus_log_set))
        val why = string(R.string.progression_why_content_description, "Back Squat")
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescriptionExactly(why)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(why).performScrollTo().performClick()

        assertOnTheRecommendationScreen()
    }

    private fun assertOnTheRecommendationScreen() {
        waitForText(string(R.string.progression_go_with_it))
        composeRule.onNodeWithText(string(R.string.progression_screen_title)).assertIsDisplayed()
        composeRule.onNodeWithText("BACK SQUAT").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.progression_result_increase_load)).assertIsDisplayed()
    }

    /** The workout and recommendation destinations, registered exactly as `RepFlowNavHost` does. */
    private fun setWorkoutGraph() {
        val workoutViewModel = workoutViewModel()
        composeRule.setContent {
            RepFlowTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = RepFlowDestinations.WORKOUT) {
                    composable(RepFlowDestinations.WORKOUT) {
                        ActiveWorkoutRoute(
                            onOpenRecommendation = { id -> navController.navigate(RepFlowDestinations.progressionRoute(id.value)) },
                            onCreateExercise = {},
                            onLeaveWorkout = {},
                            onWorkoutFinished = {},
                            viewModel = workoutViewModel,
                        )
                    }
                    composable(
                        route = RepFlowDestinations.PROGRESSION_PATTERN,
                        arguments = listOf(navArgument(RepFlowDestinations.PROGRESSION_EXERCISE_ARG) { type = NavType.StringType }),
                    ) { entry ->
                        val argument = checkNotNull(entry.arguments?.getString(RepFlowDestinations.PROGRESSION_EXERCISE_ARG))
                        ProgressionRecommendationRoute(
                            onBack = { navController.popBackStack() },
                            viewModel = remember(argument) { recommendationViewModel(argument) },
                        )
                    }
                }
            }
        }
    }

    private fun recommendationViewModel(argument: String): ProgressionRecommendationViewModel =
        ProgressionRecommendationViewModel(
            savedStateHandle = SavedStateHandle(mapOf(RepFlowDestinations.PROGRESSION_EXERCISE_ARG to argument)),
            repository = recommendations,
            getExercise = GetExercise(LocalExerciseRepository(database.exerciseDao())),
            recordManualOverride = RecordManualOverride(recommendations, clock),
            observeReadiness = ObserveReadiness(LocalRecoveryRepository(database.recoveryEntryDao())),
            clock = clock,
        )

    private fun workoutRepository(): LocalWorkoutRepository =
        LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao())

    private fun workoutViewModel(): ActiveWorkoutViewModel {
        val workouts = workoutRepository()
        val plans =
            LocalTrainingPlanRepository(
                database,
                database.trainingPlanDao(),
                database.trainingPlanVersionDao(),
                database.plannedExerciseDao(),
            )
        val exercises = LocalExerciseRepository(database.exerciseDao())
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
            settingsRepository = LocalSettingsRepository(database),
            restNotificationCanceller = RestNotificationCanceller { },
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
                createdAt = computedAt,
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
