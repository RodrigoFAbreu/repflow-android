package com.repflow.app.presentation.archived

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.repflow.app.application.exercise.ArchiveExercise
import com.repflow.app.application.exercise.ExercisePersistenceError
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.RestoreExercise
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.ArchiveTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.ObserveTrainingPlans
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.trainingplan.RestoreTrainingPlan
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedViewModelTest {
    private val created = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(created)
    private val exerciseRepository = InMemoryExerciseRepository()
    private val planRepository = InMemoryTrainingPlanRepository()
    private val createPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, SequentialIdentifierGenerator("plan"))
    private val viewModel =
        ArchivedViewModel(
            ObserveExercises(exerciseRepository),
            ObserveTrainingPlans(planRepository),
            RestoreExercise(exerciseRepository, clock),
            RestoreTrainingPlan(planRepository, clock),
            ArchiveExercise(exerciseRepository, clock),
            ArchiveTrainingPlan(planRepository, clock),
        )

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun seedExercise(
        id: String,
        name: String,
        archivedAt: Instant?,
    ): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            when (val result = ExerciseName.create(name)) {
                is DomainResult.Success -> {
                    val fresh =
                        Exercise.create(
                            id = exerciseId,
                            name = result.value,
                            trackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
                            instructions = null,
                            defaultLoadIncrement = null,
                            defaultRestDuration = null,
                            origin = ExerciseOrigin.CUSTOM,
                            createdAt = created,
                        )
                    check(fresh is DomainResult.Success)
                    fresh.value.let { if (archivedAt != null) it.archive(archivedAt) else it }
                }

                is DomainResult.Failure -> {
                    error("bad name")
                }
            }
        exerciseRepository.seed(exercise)
        return exerciseId
    }

    private suspend fun seedPlan(
        name: String,
        exerciseId: ExerciseId,
        archivedAt: Instant? = null,
    ): TrainingPlanId {
        val command =
            CreateTrainingPlanCommand(
                name = name,
                plannedExercises =
                    listOf(
                        PlannedExerciseInput(
                            exerciseId = exerciseId.value,
                            order = 0,
                            targetSets = 3,
                            targetKind = PlannedExerciseTargetKind.REPS,
                            repMin = 8,
                            repMax = 12,
                            durationMinSeconds = null,
                            durationMaxSeconds = null,
                            restSeconds = 90,
                            isOptional = false,
                        ),
                    ),
            )
        val result = createPlan(command)
        check(result is DomainResult.Success) { "Expected plan creation to succeed but was $result" }
        if (archivedAt != null) {
            clock.advanceTo(archivedAt)
            ArchiveTrainingPlan(planRepository, clock)(result.value)
            clock.advanceTo(created)
        }
        return result.value
    }

    private suspend fun ReceiveTurbine<ArchivedUiState>.awaitLoaded(
        until: (ArchivedContent.Loaded) -> Boolean = { true },
    ): ArchivedUiState {
        while (true) {
            val state = awaitItem()
            val content = state.content
            if (content is ArchivedContent.Loaded && until(content)) return state
        }
    }

    @Test
    fun `both lists hold only what is archived, most recently archived first`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedExercise("e1", "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))
            seedExercise("e2", "Smith Machine Squat", Instant.parse("2026-09-12T10:00:00Z"))
            val active = seedExercise("e3", "Barbell Row", archivedAt = null)
            seedPlan("Old Plan", active, archivedAt = Instant.parse("2026-02-02T10:00:00Z"))
            seedPlan("Current Plan", active)

            viewModel.uiState.test {
                val loaded = awaitLoaded().content as ArchivedContent.Loaded

                assertEquals(listOf("Smith Machine Squat", "Cable Fly"), loaded.exercises.map { it.name })
                assertEquals(Instant.parse("2026-09-12T10:00:00Z"), loaded.exercises.first().archivedAt)
                assertEquals(listOf("Old Plan"), loaded.plans.map { it.name })
            }
        }

    @Test
    fun `nothing archived loads as two empty lists`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))

            viewModel.uiState.test {
                val loaded = awaitLoaded().content as ArchivedContent.Loaded

                assertTrue(loaded.exercises.isEmpty())
                assertTrue(loaded.plans.isEmpty())
            }
        }

    @Test
    fun `restoring an exercise removes its row at once and queues a restored message`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val id = seedExercise("e1", "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))

            viewModel.uiState.test {
                val item = (awaitLoaded().content as ArchivedContent.Loaded).exercises.single()

                viewModel.onRestoreClicked(item)

                var state = awaitItem()
                while (state.messages.isEmpty() || (state.content as? ArchivedContent.Loaded)?.exercises?.isNotEmpty() != false) {
                    state = awaitItem()
                }
                assertNull(exerciseRepository.findById(id)?.archivedAt)
                val message = state.messages.single() as ArchivedMessage.Restored
                assertEquals("Cable Fly", message.name)
                assertEquals(ArchivedTarget.Exercise(id), message.target)
            }
        }

    @Test
    fun `restoring a plan restores it and undo archives it again`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise("e1", "Barbell Row", archivedAt = null)
            val planId = seedPlan("Old Plan", exerciseId, archivedAt = Instant.parse("2026-02-02T10:00:00Z"))

            viewModel.uiState.test {
                val item = (awaitLoaded().content as ArchivedContent.Loaded).plans.single()

                viewModel.onRestoreClicked(item)
                var state = awaitItem()
                while (state.messages.isEmpty() || (state.content as? ArchivedContent.Loaded)?.plans?.isEmpty() != true) {
                    state = awaitItem()
                }
                assertNull(requireNotNull(planRepository.findOverviewByPlanId(planId)).plan.archivedAt)

                viewModel.onUndoRestoreClicked((state.messages.single() as ArchivedMessage.Restored).target)
                while ((state.content as? ArchivedContent.Loaded)?.plans?.isEmpty() != false) state = awaitItem()

                assertNotNull(requireNotNull(planRepository.findOverviewByPlanId(planId)).plan.archivedAt)
                assertEquals(listOf("Old Plan"), (state.content as ArchivedContent.Loaded).plans.map { it.name })
            }
        }

    @Test
    fun `a failed restore queues the generic failure and the row stays`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val id = seedExercise("e1", "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))
            exerciseRepository.nextUpdateFailure =
                ExercisePersistenceError.Unavailable

            viewModel.uiState.test {
                val item = (awaitLoaded().content as ArchivedContent.Loaded).exercises.single()

                viewModel.onRestoreClicked(item)

                var state = awaitItem()
                while (state.messages.isEmpty()) state = awaitItem()
                assertTrue(state.messages.single() is ArchivedMessage.OperationFailed)
                assertNotNull(exerciseRepository.findById(id)?.archivedAt)
                assertEquals(1, (state.content as ArchivedContent.Loaded).exercises.size)
            }
        }

    @Test
    fun `onMessageShown removes only that message`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedExercise("e1", "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))
            seedExercise("e2", "Leg Press", Instant.parse("2026-08-04T10:00:00Z"))

            viewModel.uiState.test {
                val items = (awaitLoaded().content as ArchivedContent.Loaded).exercises
                viewModel.onRestoreClicked(items[0])
                var state = awaitItem()
                while (state.messages.isEmpty()) state = awaitItem()
                viewModel.onRestoreClicked(items[1])
                while (state.messages.size < 2) state = awaitItem()
                val first = state.messages.first().id
                val second = state.messages.last().id

                viewModel.onMessageShown(first)
                while (state.messages.any { it.id == first }) state = awaitItem()

                assertEquals(listOf(second), state.messages.map { it.id })
            }
        }

    @Test
    fun `an observation failure shows the failed state and retry recovers`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedExercise("e1", "Cable Fly", Instant.parse("2026-08-03T10:00:00Z"))
            exerciseRepository.observeFailureOnNextSubscription = true

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.content != ArchivedContent.ObservationFailed) state = awaitItem()

                viewModel.onRetry()
                val loaded = awaitLoaded().content as ArchivedContent.Loaded

                assertEquals(listOf("Cable Fly"), loaded.exercises.map { it.name })
            }
        }
}
