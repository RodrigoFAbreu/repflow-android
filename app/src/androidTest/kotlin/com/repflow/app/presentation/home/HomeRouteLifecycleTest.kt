package com.repflow.app.presentation.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.repflow.app.R
import com.repflow.app.application.common.Clock
import com.repflow.app.application.common.IdentifierGenerator
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.history.ObserveRecentTraining
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.RecordRecoveryEntryCommand
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.data.exercise.LocalExerciseRepository
import com.repflow.app.data.recovery.LocalRecoveryRepository
import com.repflow.app.data.trainingplan.LocalTrainingPlanRepository
import com.repflow.app.data.workout.LocalWorkoutRepository
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.infrastructure.database.RepFlowDatabase
import com.repflow.app.presentation.RepFlowTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The route's half of "today is re-derived" (remediation-1 plan CP5 item 4):
 * after a stop and a start inside the 5-second `WhileSubscribed` window the
 * upstream never restarts, and the midnight wait is hours away in real time,
 * so only `HomeRoute`'s `ON_START` effect can move Home to the new day. Run
 * over a real in-memory Room database, as the DAO tests open one.
 */
@RunWith(AndroidJUnit4::class)
class HomeRouteLifecycleTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private val day = LocalDate.parse("2026-08-11")
    private lateinit var database: RepFlowDatabase

    /** A wall clock the test sets directly, as a device returning from deep sleep would read one. */
    private class SettableClock(
        var current: Instant,
    ) : Clock {
        override fun now(): Instant = current
    }

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RepFlowDatabase::class.java)
                .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aReturnOnANewDayShowsThatDaysEmptyStateWithoutResubscribing() {
        val clock = SettableClock(at(day.atTime(10, 0)))
        val recoveryRepository = LocalRecoveryRepository(database.recoveryEntryDao())
        runBlocking {
            val result =
                RecordRecoveryEntry(recoveryRepository, clock) { "recovery-1" }(
                    RecordRecoveryEntryCommand(
                        date = day,
                        sleepQuality = 4,
                        energy = 3,
                        legDoms = 2,
                        heelStiffness = 1,
                        painWhileWalking = 0,
                        heavyLegs = 2,
                        futsalInPrevious24h = false,
                        futsalExpectedNext24h = false,
                        notes = null,
                    ),
                )
            assertTrue(result is DomainResult.Success)
        }
        val viewModel = viewModel(clock, recoveryRepository)
        composeRule.setContent {
            RepFlowTheme {
                HomeRoute(
                    onOpenWorkout = {},
                    onFinishWorkout = {},
                    onSettingsClick = {},
                    onLogRecoveryClick = {},
                    onCreatePlanClick = {},
                    viewModel = viewModel,
                )
            }
        }
        val ready = composeRule.activity.getString(R.string.readiness_band_ready)
        val scoreDescription = composeRule.activity.getString(R.string.home_readiness_content_description, 75, ready)
        val emptyMessage = composeRule.activity.getString(R.string.home_recovery_empty_message)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasContentDescriptionExactly(scoreDescription)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(scoreDescription).assertIsDisplayed()

        clock.current = at(day.plusDays(1).atTime(7, 0))
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasTextExactly(emptyMessage)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(emptyMessage).assertIsDisplayed()
    }

    private fun at(dateTime: LocalDateTime): Instant = dateTime.atZone(zone).toInstant()

    private fun viewModel(
        clock: Clock,
        recoveryRepository: LocalRecoveryRepository,
    ): HomeViewModel {
        val workoutRepository =
            LocalWorkoutRepository(
                database,
                database.workoutSessionDao(),
                database.workoutExerciseDao(),
                database.workoutSetDao(),
            )
        val trainingPlanRepository =
            LocalTrainingPlanRepository(
                database,
                database.trainingPlanDao(),
                database.trainingPlanVersionDao(),
                database.plannedExerciseDao(),
            )
        var nextId = 0
        val ids = IdentifierGenerator { "id-${++nextId}" }
        return HomeViewModel(
            clock = clock,
            observeReadiness = ObserveReadiness(recoveryRepository),
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workoutRepository),
            observeTrainingPlans = ObserveTrainingPlans(trainingPlanRepository),
            observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(trainingPlanRepository),
            observeRecentTraining = ObserveRecentTraining(workoutRepository),
            startWorkoutSession = StartWorkoutSession(workoutRepository, clock, ids),
            startWorkoutSessionFromPlan =
                StartWorkoutSessionFromPlan(
                    workoutRepository,
                    GetExercise(LocalExerciseRepository(database.exerciseDao())),
                    clock,
                    ids,
                ),
            abandonWorkoutSession = AbandonWorkoutSession(workoutRepository, clock),
        )
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
