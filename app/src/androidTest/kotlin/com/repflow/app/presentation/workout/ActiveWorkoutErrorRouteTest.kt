package com.repflow.app.presentation.workout

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.progression.ComputeProgressionRecommendation
import com.repflow.app.application.recovery.GetWorkoutDayContext
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.AddWorkoutExercise
import com.repflow.app.application.workout.AddWorkoutExerciseCommand
import com.repflow.app.application.workout.AdjustRestTimer
import com.repflow.app.application.workout.CompleteWorkoutSession
import com.repflow.app.application.workout.EditWorkoutSet
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.RecordWorkoutSet
import com.repflow.app.application.workout.RecordWorkoutSetCommand
import com.repflow.app.application.workout.SkipRestTimer
import com.repflow.app.application.workout.StartRestTimer
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionCommand
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.application.workout.UndoLastWorkoutSet
import com.repflow.app.application.workout.WorkoutPersistenceError
import com.repflow.app.application.workout.WorkoutRepository
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
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.presentation.RepFlowTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The route's wiring of a failed workout action (A-1) and of `Try again` on the
 * load-failure state (A-12), over a real [ActiveWorkoutViewModel] and an in-memory
 * Room database: the app has no fault hook in `app/src/main`, so
 * [FailingWorkoutRepository] forces the failures here. The screen and ViewModel
 * tests each stop at their own edge; only these walks fail when a route line is
 * miswired.
 */
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutErrorRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock { Instant.now() }
    private val exerciseId = ExerciseId("exercise-squat")
    private lateinit var database: RepFlowDatabase
    private lateinit var workouts: FailingWorkoutRepository
    private var nextId = 0
    private val ids = IdentifierGenerator { "id-${++nextId}" }

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        workouts =
            FailingWorkoutRepository(
                LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao()),
            )
        runBlocking {
            success(LocalExerciseRepository(database.exerciseDao()).insert(exercise()))
            // A running rest would raise the system's notification prompt over the walk.
            success(LocalSettingsRepository(database).update { it.copy(restTimerAutoStart = false) })
        }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aFailedLogSetShowsTheMessageAgainOnASecondFailureAndALaterSaveClearsTheEntry() {
        val sessionId = startSessionWithOneLoggedSet()
        render()
        openExerciseAndEnterDetail()

        workouts.nextUpdateFailure = WorkoutPersistenceError.Unavailable
        composeRule.onNodeWithText(string(R.string.workout_focus_log_warmup)).performClick()

        waitForText(string(R.string.workout_active_action_failed))
        assertEntryKept()
        assertEquals(1, loggedSets(sessionId))

        // The same failure shows again only if the route hands `onErrorShown` to the ViewModel: nothing else clears the field.
        waitForTextGone(string(R.string.workout_active_action_failed))
        workouts.nextUpdateFailure = WorkoutPersistenceError.Unavailable
        composeRule.onNodeWithText(string(R.string.workout_focus_log_warmup)).performClick()
        waitForText(string(R.string.workout_active_action_failed))
        assertEntryKept()

        composeRule.onNodeWithText(string(R.string.workout_focus_log_warmup)).performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) { loggedSets(sessionId) == 2 }
        waitForText(string(R.string.workout_focus_log_set))
        composeRule.onNodeWithText(string(R.string.workout_active_set_detail_toggle)).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodes(isSelectable() and isSelected()).assertCountEquals(0)
    }

    /** A-12: the failed observation has completed, so only the route handing `onRetry` to the ViewModel gets out of this state. */
    @Test
    fun tryAgainOnTheLoadFailureStateShowsTheBoardWithTheSession() {
        startSessionWithOneLoggedSet()
        workouts.failNextObservation = true
        render()

        waitForText(string(R.string.workout_active_observation_failed))
        composeRule.onNodeWithText(string(R.string.workout_active_retry)).performClick()

        waitForText(EXERCISE_NAME)
        composeRule.onNodeWithText(EXERCISE_NAME).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.workout_active_observation_failed)).assertDoesNotExist()
    }

    private fun assertEntryKept() {
        composeRule.onNode(hasText("7") and isSelectable()).performScrollTo().assertIsSelected()
        composeRule.onNodeWithText(string(R.string.workout_focus_log_warmup)).assertIsDisplayed()
    }

    private fun render() {
        val viewModel = workoutViewModel()
        composeRule.setContent {
            RepFlowTheme {
                ActiveWorkoutRoute(
                    focusEntries = rememberSaveable(saver = FocusEntries.Saver) { FocusEntries() },
                    onOpenRecommendation = {},
                    onCreateExercise = {},
                    onLeaveWorkout = {},
                    onWorkoutFinished = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    /** Opens the exercise's focus mode from its board row, then enters an RPE and the warm-up flag. */
    private fun openExerciseAndEnterDetail() {
        waitForText(EXERCISE_NAME)
        composeRule.onNodeWithText(EXERCISE_NAME).performClick()
        waitForText(string(R.string.workout_focus_log_set))
        composeRule.onNodeWithText(string(R.string.workout_active_set_detail_toggle)).performScrollTo().performClick()
        composeRule.onNode(hasText("7") and isSelectable()).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.workout_active_warmup_label)).performScrollTo().performClick()
    }

    /** One logged set seeds the steppers from this session, so `Log set` is enabled without typing a weight. */
    private fun startSessionWithOneLoggedSet(): WorkoutSessionId =
        runBlocking {
            val sessionId = success(StartWorkoutSession(workouts, clock, ids)(StartWorkoutSessionCommand(null)))
            val workoutExerciseId =
                success(
                    AddWorkoutExercise(workouts, ids)(
                        AddWorkoutExerciseCommand(
                            sessionId = sessionId,
                            exerciseId = exerciseId,
                            exerciseNameSnapshot = EXERCISE_NAME,
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            plannedExerciseId = null,
                        ),
                    ),
                )
            success(
                RecordWorkoutSet(workouts, clock, ids)(
                    RecordWorkoutSetCommand(
                        sessionId = sessionId,
                        exerciseId = workoutExerciseId,
                        load = 100.0,
                        reps = 5,
                        durationSeconds = null,
                        rpe = null,
                        isWarmup = false,
                    ),
                ),
            )
            sessionId
        }

    private fun loggedSets(sessionId: WorkoutSessionId): Int =
        runBlocking {
            checkNotNull(workouts.findById(sessionId))
                .exercises
                .single()
                .sets.size
        }

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun waitForText(text: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTextGone(text: String) {
        composeRule.waitUntil(MESSAGE_GONE_TIMEOUT_MILLIS) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isEmpty() }
    }

    private fun workoutViewModel(): ActiveWorkoutViewModel {
        val plans =
            LocalTrainingPlanRepository(
                database,
                database.trainingPlanDao(),
                database.trainingPlanVersionDao(),
                database.plannedExerciseDao(),
            )
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
            editWorkoutSet = EditWorkoutSet(workouts, clock),
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
            workoutRepository = workouts,
        )
    }

    private fun exercise(): Exercise =
        success(
            Exercise.create(
                id = exerciseId,
                name = success(ExerciseName.create(EXERCISE_NAME)),
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

    /**
     * The real repository, except that [nextUpdateFailure] fails the next `update` once, and
     * [failNextObservation] fails the next `observeActiveSession` subscription once, as a Room `Flow` that throws would.
     */
    private class FailingWorkoutRepository(
        private val delegate: WorkoutRepository,
    ) : WorkoutRepository by delegate {
        @Volatile
        var nextUpdateFailure: WorkoutPersistenceError? = null

        @Volatile
        var failNextObservation: Boolean = false

        override fun observeActiveSession(): Flow<WorkoutSession?> =
            flow {
                if (failNextObservation) {
                    failNextObservation = false
                    error("Simulated observation failure")
                }
                emitAll(delegate.observeActiveSession())
            }

        override suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
            val failure = nextUpdateFailure ?: return delegate.update(session)
            nextUpdateFailure = null
            return DomainResult.Failure(failure)
        }
    }

    private companion object {
        const val EXERCISE_NAME = "Back Squat"
        const val TIMEOUT_MILLIS = 5_000L

        /** A short snackbar is up for 4 s, and `waitUntil` advances the test clock by a frame at a time. */
        const val MESSAGE_GONE_TIMEOUT_MILLIS = 15_000L
    }
}
