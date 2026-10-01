package com.repflow.app.presentation.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.common.Clock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.history.ObserveRecentTraining
import com.repflow.app.application.recovery.InMemoryRecoveryRepository
import com.repflow.app.application.recovery.ObserveReadiness
import com.repflow.app.application.recovery.RecordRecoveryEntry
import com.repflow.app.application.recovery.RecordRecoveryEntryCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlanVersionLabels
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.workout.AbandonWorkoutSession
import com.repflow.app.application.workout.InMemoryWorkoutRepository
import com.repflow.app.application.workout.ObserveActiveWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSession
import com.repflow.app.application.workout.StartWorkoutSessionFromPlan
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseId
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlan
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.domain.trainingplan.TrainingPlanVersion
import com.repflow.app.domain.trainingplan.TrainingPlanVersionId
import com.repflow.app.domain.workout.WorkoutSession
import com.repflow.app.domain.workout.WorkoutSessionId
import com.repflow.app.domain.workout.WorkoutSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Home's ViewModel (remediation-1 CP5). The first three tests are the Home
 * half of CP4's readiness reactivity, and the two date cases plan CP5 item 4
 * names: a screen left on across midnight, and a return from deep sleep where
 * only `onForeground()` re-reads the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.parse("2026-08-11")
    private val nextDay: LocalDate = day.plusDays(1)

    private val recoveryRepository = InMemoryRecoveryRepository()
    private val workoutRepository = InMemoryWorkoutRepository()
    private val exerciseRepository = InMemoryExerciseRepository()
    private val trainingPlanRepository = InMemoryTrainingPlanRepository()

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saving today's check-in replaces the log prompt with the score on the same subscription`() =
        runTest {
            val clock = startAt(day.atTime(10, 0))
            val viewModel = viewModel(clock)

            viewModel.uiState.test {
                awaitUntil { it.readiness == HomeReadiness.NotLogged }

                recordCheckIn(clock, day)

                val logged = awaitUntil { it.readiness is HomeReadiness.Logged }.readiness as HomeReadiness.Logged
                assertEquals(75, logged.score.score)
            }
        }

    @Test
    fun `yesterday's check-in is not today's score`() =
        runTest {
            val clock = startAt(day.atTime(10, 0))
            recordCheckIn(clock, day.minusDays(1))
            val viewModel = viewModel(clock)

            viewModel.uiState.test {
                val state = awaitUntil { it.readiness != HomeReadiness.Loading }
                assertEquals(HomeReadiness.NotLogged, state.readiness)
                assertEquals(day, state.date)
            }
        }

    @Test
    fun `a screen left on across midnight moves to the new day, then scores its check-in`() =
        runTest {
            val clock = startAt(day.atTime(23, 59))
            recordCheckIn(clock, day)
            val viewModel = viewModel(clock)

            viewModel.uiState.test {
                awaitUntil { it.readiness is HomeReadiness.Logged }

                advanceTimeBy(Duration.ofSeconds(61).toMillis())

                val nextMorning = awaitUntil { it.date == nextDay }
                assertEquals(HomeReadiness.NotLogged, nextMorning.readiness)

                recordCheckIn(clock, nextDay)

                assertTrue(awaitUntil { it.readiness is HomeReadiness.Logged }.date == nextDay)
            }
        }

    @Test
    fun `after a deep sleep only onForeground re-reads the clock, on the same subscription`() =
        runTest {
            val clock = startAt(day.atTime(10, 0))
            recordCheckIn(clock, day)
            val viewModel = viewModel(clock)

            viewModel.uiState.test {
                awaitUntil { it.readiness is HomeReadiness.Logged }

                // The wall clock jumps to 07:00 the next day while uptime - and so the
                // pending midnight wait, 14 hours away - does not move.
                clock.jumpTo(nextDay.atTime(7, 0))
                expectNoEvents()

                viewModel.onForeground()

                val state = awaitUntil { it.date == nextDay }
                assertEquals(HomeReadiness.NotLogged, state.readiness)
            }
        }

    @Test
    fun `with no active plan the start card is the first-run card, and Empty workout starts and opens the workout`() =
        runTest {
            val viewModel = viewModel(startAt(day.atTime(10, 0)))

            viewModel.uiState.test {
                assertEquals(HomeStartCard.NoPlan, awaitUntil { it.start != HomeStartCard.Loading }.start)

                viewModel.onStartWorkout(null)

                val started = awaitUntil { it.openWorkout && it.activeWorkout != null }
                assertNull(started.activeWorkout?.planName)
                assertEquals(0, started.activeWorkout?.setsLogged)

                viewModel.onWorkoutOpened()
                assertTrue(!awaitItem().openWorkout)
            }
        }

    @Test
    fun `the start card offers the plan last trained, and starting it seeds that plan`() =
        runTest {
            val clock = startAt(day.atTime(10, 0))
            val alpha = seedPlan("plan-a", "Alpha")
            val beta = seedPlan("plan-b", "Beta")
            completeSessionFrom(beta.id, endedAt = clock.now().minusSeconds(86_400))
            val viewModel = viewModel(clock)

            viewModel.uiState.test {
                val state = awaitUntil { it.start is HomeStartCard.Plan && it.lastWorkout is HomeLastWorkout.Summary }
                assertEquals("Beta", (state.start as HomeStartCard.Plan).option.planName)
                assertEquals(listOf("Alpha", "Beta"), state.startOptions.map { it.planName })
                assertEquals(3, state.startOptions.first { it.versionId == alpha.id }.workingSetCount)
                assertEquals("Beta", (state.lastWorkout as HomeLastWorkout.Summary).planName)

                viewModel.onStartWorkout(beta.id)

                val started = awaitUntil { it.activeWorkout != null }
                assertEquals("Beta", started.activeWorkout?.planName)
                assertEquals(beta.id, workoutRepository.findActiveSession()?.trainingPlanVersionId)
            }
        }

    @Test
    fun `with no workout trained from a plan the start card falls back to the first active plan`() =
        runTest {
            seedPlan("plan-a", "Alpha")
            seedPlan("plan-b", "Beta")
            val viewModel = viewModel(startAt(day.atTime(10, 0)))

            viewModel.uiState.test {
                val state = awaitUntil { it.start is HomeStartCard.Plan }
                assertEquals("Alpha", (state.start as HomeStartCard.Plan).option.planName)
                assertEquals(HomeLastWorkout.None, state.lastWorkout)
            }
        }

    @Test
    fun `starting a plan that is no longer active surfaces an error and starts nothing`() =
        runTest {
            val viewModel = viewModel(startAt(day.atTime(10, 0)))

            viewModel.uiState.test {
                awaitUntil { it.start != HomeStartCard.Loading }

                viewModel.onStartWorkout(TrainingPlanVersionId("gone"))

                assertEquals(HomeErrorReason.PLAN_NOT_FOUND, awaitUntil { it.error != null }.error)
                assertNull(workoutRepository.findActiveSession())
                viewModel.onErrorShown()
                assertNull(awaitItem().error)
            }
        }

    @Test
    fun `abandoning from Home marks the session abandoned and keeps it`() =
        runTest {
            val viewModel = viewModel(startAt(day.atTime(10, 0)))

            viewModel.uiState.test {
                awaitUntil { it.start != HomeStartCard.Loading }
                viewModel.onStartWorkout(null)
                val sessionId = awaitUntil { it.activeWorkout != null }.activeWorkout!!.sessionId

                viewModel.onAbandonWorkout(sessionId)

                awaitUntil { it.activeWorkout == null }
                assertEquals(WorkoutSessionStatus.ABANDONED, workoutRepository.findById(sessionId)?.status)
            }
        }

    // --- fixtures ---------------------------------------------------------------------------

    /** A [Clock] on the test scheduler's virtual time, plus a wall-clock jump that uptime never sees. */
    private class VirtualClock(
        private val scheduler: TestCoroutineScheduler,
        private val origin: Instant,
    ) : Clock {
        private var jump: Duration = Duration.ZERO

        override fun now(): Instant = origin.plusMillis(scheduler.currentTime).plus(jump)

        fun jumpTo(instant: Instant) {
            jump = jump.plus(Duration.between(now(), instant))
        }
    }

    private fun VirtualClock.jumpTo(dateTime: LocalDateTime) = jumpTo(dateTime.atZone(zone).toInstant())

    private fun TestScope.startAt(dateTime: LocalDateTime): VirtualClock {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        return VirtualClock(testScheduler, dateTime.atZone(zone).toInstant().minusMillis(testScheduler.currentTime))
    }

    private fun viewModel(clock: Clock): HomeViewModel {
        val ids = SequentialIdentifierGenerator(prefix = "id")
        return HomeViewModel(
            clock = clock,
            observeReadiness = ObserveReadiness(recoveryRepository),
            observeActiveWorkoutSession = ObserveActiveWorkoutSession(workoutRepository),
            observeTrainingPlans = ObserveTrainingPlans(trainingPlanRepository),
            observeTrainingPlanVersionLabels = ObserveTrainingPlanVersionLabels(trainingPlanRepository),
            observeRecentTraining = ObserveRecentTraining(workoutRepository),
            startWorkoutSession = StartWorkoutSession(workoutRepository, clock, ids),
            startWorkoutSessionFromPlan = StartWorkoutSessionFromPlan(workoutRepository, GetExercise(exerciseRepository), clock, ids),
            abandonWorkoutSession = AbandonWorkoutSession(workoutRepository, clock),
        )
    }

    /** The prototype's seed check-in (sleep 4, energy 3, DOMS 2, heel 1, pain 0, heavy legs 2): 75, Ready. */
    private suspend fun recordCheckIn(
        clock: Clock,
        date: LocalDate,
    ) {
        val result =
            RecordRecoveryEntry(recoveryRepository, clock, SequentialIdentifierGenerator(prefix = "rec-$date"))(
                RecordRecoveryEntryCommand(
                    date = date,
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
        assertTrue("check-in not recorded: $result", result is DomainResult.Success)
    }

    private suspend fun seedPlan(
        id: String,
        name: String,
    ): TrainingPlanVersion {
        val created = Instant.parse("2026-08-01T00:00:00Z")
        val exercise =
            success(
                Exercise.create(
                    id = ExerciseId("exercise-$id"),
                    name = success(ExerciseName.create("Squat $id")),
                    trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = created,
                ),
            )
        exerciseRepository.seed(exercise)
        val plan = success(TrainingPlan.create(id = TrainingPlanId(id), name = success(TrainingPlanName.create(name)), createdAt = created))
        val version =
            success(
                TrainingPlanVersion.create(
                    id = TrainingPlanVersionId("$id-v1"),
                    planId = plan.id,
                    versionNumber = 1,
                    plannedExercises =
                        listOf(
                            PlannedExercise(
                                id = PlannedExerciseId("$id-planned"),
                                exerciseId = exercise.id,
                                order = 0,
                                targetSets = success(TargetSets.create(3)),
                                target = PlannedExerciseTarget.Reps(success(RepRange.create(8, 12))),
                                restDuration = null,
                                isOptional = false,
                            ),
                        ),
                    note = null,
                    createdAt = created,
                ),
            )
        success(trainingPlanRepository.createPlanWithFirstVersion(plan, version))
        return version
    }

    private suspend fun completeSessionFrom(
        versionId: TrainingPlanVersionId,
        endedAt: Instant,
    ) {
        val session =
            success(
                WorkoutSession.reconstruct(
                    id = WorkoutSessionId("done-${versionId.value}"),
                    trainingPlanVersionId = versionId,
                    status = WorkoutSessionStatus.COMPLETED,
                    startedAt = endedAt.minusSeconds(3_660),
                    endedAt = endedAt,
                    exercises = emptyList(),
                ),
            )
        success(workoutRepository.insert(session))
    }

    private suspend fun ReceiveTurbine<HomeUiState>.awaitUntil(predicate: (HomeUiState) -> Boolean): HomeUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    private fun <T> success(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was ${result.error}")
        }
}
