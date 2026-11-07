package com.repflow.app.presentation.workout

import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

/**
 * `Start` ends the rest of a set still on its way (`timed-set-clock`, implementation
 * review I1). There are two moments the screen can't know a rest is coming: while
 * `Log set` is still saving, and after it has saved but before the screen has seen what
 * it stored. Both run through the real route, ViewModel and action queue over a real
 * in-memory Room database, and [HoldingWorkoutRepository] holds the moment open. So does
 * the plain case, a rest already on the screen, and each reads the rest's real alarm.
 */
@RunWith(AndroidJUnit4::class)
class ClockStartEndsRestRouteTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val clock = Clock { Instant.now() }
    private lateinit var database: RepFlowDatabase
    private lateinit var workouts: HoldingWorkoutRepository
    private var nextId = 0
    private val ids = IdentifierGenerator { "id-${++nextId}" }

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
        workouts =
            HoldingWorkoutRepository(
                LocalWorkoutRepository(database, database.workoutSessionDao(), database.workoutExerciseDao(), database.workoutSetDao()),
            )
        // The rest is real here, so neither of its prompts may come up over what is being tapped.
        RestTimerAlarmScheduler.markExactAlarmPrompted(ApplicationProvider.getApplicationContext())
        runBlocking { success(LocalSettingsRepository(database).update { it.copy(restTimerNotification = false) }) }
    }

    @After
    fun closeDatabase() {
        // A failed test can leave its rest's real alarm behind.
        RestTimerAlarmScheduler.cancel(ApplicationProvider.getApplicationContext())
        database.close()
    }

    /**
     * The queue holds `Log set`, then `Start again`, so the save does run first: its rest reaches
     * the screen and its real alarm is set until `Start again`'s turn comes, which is held here to see that.
     */
    @Test
    fun aStartAgainTappedWhileTheSetIsSavingEndsTheRestThatSaveStartsAndCancelsItsAlarm() {
        val sessionId = startSessionWithPlank()
        val stopped = openPlankAndStopAHold()
        val writes = CompletableDeferred<Unit>().also { workouts.writes = it }
        val restEnding = CompletableDeferred<Unit>().also { workouts.restEnding = it }

        composeRule.onNodeWithText(string(R.string.workout_focus_log_set)).performClick()
        composeRule.waitUntil("the save to reach its write", TIMEOUT_MILLIS) { workouts.writesWaiting.get() > 0 }
        composeRule.onNodeWithText(string(R.string.workout_rest_resting)).assertDoesNotExist()
        tap(R.string.workout_focus_clock_start_again)
        writes.complete(Unit)

        waitForText(string(R.string.workout_rest_resting))
        composeRule.waitUntil("the rest's real alarm to be set", TIMEOUT_MILLIS) { restAlarmIsPending() }
        restEnding.complete(Unit)

        assertTheRestIsClearedAndTheNewClockRuns(sessionId, stopped)
    }

    /** The rest goes before the screen sees it, so no alarm is ever set for it. */
    @Test
    fun aStartTappedBeforeTheSavedSetsRestReachesTheScreenEndsThatRest() {
        val sessionId = startSessionWithPlank()
        val stopped = openPlankAndStopAHold()
        val observation = CompletableDeferred<Unit>().also { workouts.observation = it }

        composeRule.onNodeWithText(string(R.string.workout_focus_log_set)).performClick()
        composeRule.waitUntil("the save to store its rest", TIMEOUT_MILLIS) { storedRestEnd(sessionId) != null }
        // The saved set has cleared the card, and the screen has seen neither the set nor its rest.
        waitForText(string(R.string.workout_focus_clock_start))
        composeRule.onNodeWithText(string(R.string.workout_rest_resting)).assertDoesNotExist()
        tap(R.string.workout_focus_clock_start)
        observation.complete(Unit)

        assertTheRestIsClearedAndTheNewClockRuns(sessionId, stopped)
    }

    @Test
    fun aStartWithARestOnTheScreenEndsItAndCancelsItsAlarm() {
        val sessionId = startSessionWithPlank()
        val stopped = openPlankAndStopAHold()

        composeRule.onNodeWithText(string(R.string.workout_focus_log_set)).performClick()
        waitForText(string(R.string.workout_rest_resting))
        composeRule.waitUntil("the rest's real alarm to be set", TIMEOUT_MILLIS) { restAlarmIsPending() }
        tap(R.string.workout_focus_clock_start)

        assertTheRestIsClearedAndTheNewClockRuns(sessionId, stopped)
    }

    /** Stops a short hold, which is what `Log set` then saves, and returns the time it stopped at. */
    private fun openPlankAndStopAHold(): Long {
        openPlank()
        tap(R.string.workout_focus_clock_start)
        composeRule.waitForClockReadout(atLeastSeconds = 1)
        tap(R.string.workout_focus_clock_stop)
        return composeRule.clockReadoutSeconds()
    }

    private fun assertTheRestIsClearedAndTheNewClockRuns(
        sessionId: WorkoutSessionId,
        stoppedSeconds: Long,
    ) {
        // Without the first wait this would pass between the set's write and the rest's.
        composeRule.waitUntil("the save to store its rest", TIMEOUT_MILLIS) { workouts.restWasStored }
        composeRule.waitUntil("the rest that save started to be cleared", TIMEOUT_MILLIS) { storedRestEnd(sessionId) == null }

        val resting = hasText(string(R.string.workout_rest_resting))
        composeRule.waitUntil("the rest strip to go", TIMEOUT_MILLIS) { composeRule.onAllNodes(resting).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText(string(R.string.workout_focus_clock_stop)).performScrollTo().assertIsDisplayed()
        val sets =
            runBlocking {
                checkNotNull(workouts.findById(sessionId))
                    .exercises
                    .single()
                    .sets
            }
        assertEquals(
            "The earlier set is saved exactly once, with its time",
            listOf(stoppedSeconds.toInt()),
            sets.map { it.durationSeconds },
        )
        assertNull(storedRestEnd(sessionId))
        composeRule.waitUntil("the rest's real alarm to be cancelled", TIMEOUT_MILLIS) { !restAlarmIsPending() }
    }

    /** `dumpsys alarm` lists a pending alarm as `Alarm{... <package>}`; its history of removed ones reads differently. */
    private fun restAlarmIsPending(): Boolean {
        val dump = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val lines = ParcelFileDescriptor.AutoCloseInputStream(dump).bufferedReader().use { it.readLines() }
        val packageName = composeRule.activity.packageName
        return lines.any { "Alarm{" in it && packageName in it }
    }

    private fun storedRestEnd(sessionId: WorkoutSessionId): Instant? = runBlocking { workouts.findById(sessionId)?.restTimer?.endAt }

    private fun startSessionWithPlank(): WorkoutSessionId =
        runBlocking {
            val plankId = ExerciseId("exercise-plank")
            success(LocalExerciseRepository(database.exerciseDao()).insert(plank(plankId)))
            val sessionId = success(StartWorkoutSession(workouts, clock, ids)(StartWorkoutSessionCommand(null)))
            success(
                AddWorkoutExercise(workouts, ids)(
                    AddWorkoutExerciseCommand(
                        sessionId = sessionId,
                        exerciseId = plankId,
                        exerciseNameSnapshot = PLANK,
                        trackingType = ExerciseTrackingType.DURATION,
                        plannedExerciseId = null,
                    ),
                ),
            )
            sessionId
        }

    private fun openPlank() {
        val viewModel = workoutViewModel()
        composeRule.setContent {
            RepFlowTheme {
                ActiveWorkoutRoute(
                    focusEntries = remember { FocusEntries() },
                    onOpenRecommendation = {},
                    onCreateExercise = {},
                    onLeaveWorkout = {},
                    onWorkoutFinished = {},
                    viewModel = viewModel,
                )
            }
        }
        waitForText(PLANK)
        composeRule.onNodeWithText(PLANK).performClick()
        waitForText(string(R.string.workout_focus_clock_start))
    }

    private fun tap(
        @StringRes id: Int,
    ) {
        composeRule.onNodeWithText(string(id)).performScrollTo().performClick()
    }

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun waitForText(text: String) {
        composeRule.waitUntil(
            "\"$text\" to show",
            TIMEOUT_MILLIS,
        ) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
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

    private fun plank(id: ExerciseId): Exercise =
        success(
            Exercise.create(
                id = id,
                name = success(ExerciseName.create(PLANK)),
                trackingType = ExerciseTrackingType.DURATION,
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
        const val PLANK = "Plank"
    }
}

/**
 * The real repository, whose writes, or the write that ends a rest, or whose reports to
 * the screen can be held at a point in a test. The store's own reads aren't held, so the
 * action queue goes on reading what is really there.
 */
private class HoldingWorkoutRepository(
    private val delegate: WorkoutRepository,
) : WorkoutRepository by delegate {
    @Volatile
    var writes: CompletableDeferred<Unit>? = null

    @Volatile
    var restEnding: CompletableDeferred<Unit>? = null

    @Volatile
    var observation: CompletableDeferred<Unit>? = null

    @Volatile
    var restWasStored = false
        private set

    val writesWaiting = AtomicInteger(0)

    override fun observeActiveSession(): Flow<WorkoutSession?> = delegate.observeActiveSession().onEach { observation?.await() }

    override suspend fun update(session: WorkoutSession): DomainResult<Unit, WorkoutPersistenceError> {
        writes?.let { held ->
            writesWaiting.incrementAndGet()
            held.await()
        }
        if (session.restTimer == null && delegate.findById(session.id)?.restTimer != null) restEnding?.await()
        val result = delegate.update(session)
        if (session.restTimer != null) restWasStored = true
        return result
    }
}
