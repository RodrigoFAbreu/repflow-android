package com.repflow.app.presentation.trainingplan.editor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.GetTrainingPlanDetail
import com.repflow.app.application.trainingplan.InMemoryTrainingPlanRepository
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.trainingplan.ReviseTrainingPlan
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.navigation.RepFlowDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class TrainingPlanEditorViewModelTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator(prefix = "plan")
    private val planRepository = InMemoryTrainingPlanRepository()
    private val exerciseRepository = InMemoryExerciseRepository()

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        planId: String? = null,
    ): TrainingPlanEditorViewModel {
        planId?.let { savedStateHandle[RepFlowDestinations.PLAN_EDIT_ARG] = it }
        return TrainingPlanEditorViewModel(
            savedStateHandle = savedStateHandle,
            createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids),
            reviseTrainingPlan = ReviseTrainingPlan(planRepository, exerciseRepository, clock, ids),
            getTrainingPlanDetail = GetTrainingPlanDetail(planRepository),
            observeExercises = ObserveExercises(exerciseRepository),
        )
    }

    private fun seedExercise(
        id: String = "bench-press",
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    ): ExerciseId {
        val exerciseId = ExerciseId(id)
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = exerciseId,
                    name = requireSuccess(ExerciseName.create("Exercise $id")),
                    trackingType = trackingType,
                    instructions = null,
                    defaultLoadIncrement = null,
                    defaultRestDuration = null,
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            )
        exerciseRepository.seed(exercise)
        return exerciseId
    }

    private fun <T> requireSuccess(result: DomainResult<T, *>): T =
        when (result) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> throw AssertionError("Expected success but was failure: ${result.error}")
        }

    @Test
    fun `starts in create mode with an empty row list and save disabled`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = viewModel()

            vm.uiState.test {
                val state = awaitItem()
                assertEquals(TrainingPlanEditorMode.Create, state.mode)
                assertTrue(state.rows.isEmpty())
                assertTrue(!state.isSaveEnabled)
            }
        }

    @Test
    fun `onAddRowClicked appends an unselected row and onRemoveRowClicked removes it`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = viewModel()

            vm.uiState.test {
                awaitItem() // initial

                vm.onAddRowClicked()
                val withRow = awaitItem()
                assertEquals(1, withRow.rows.size)
                val rowId = withRow.rows.single().rowId

                vm.onRemoveRowClicked(rowId)
                val withoutRow = awaitItem()
                assertTrue(withoutRow.rows.isEmpty())
            }
        }

    @Test
    fun `selecting an exercise for a row populates its tracking type and clears field errors once valid`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(1, vm.uiState.value.availableExercises.size)

            vm.onAddRowClicked()
            val rowId =
                vm.uiState.value.rows
                    .single()
                    .rowId

            vm.onRowExerciseSelected(rowId, exerciseId.value)
            val row =
                vm.uiState.value.rows
                    .single()
            assertEquals(exerciseId.value, row.exerciseId)
            assertEquals(ExerciseTrackingType.WEIGHT_AND_REPS, row.trackingType)
            assertNull(row.exerciseError)
        }

    @Test
    fun `saving a valid plan with one row succeeds and reports savedPlanId`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val vm = viewModel()
            advanceUntilIdle()

            vm.onNameChanged("Push Pull Legs")
            vm.onAddRowClicked()
            val rowId =
                vm.uiState.value.rows
                    .single()
                    .rowId
            vm.onRowExerciseSelected(rowId, exerciseId.value)
            vm.onRowTargetSetsChanged(rowId, "3")
            vm.onRowRepMinChanged(rowId, "8")
            vm.onRowRepMaxChanged(rowId, "12")
            assertTrue(vm.uiState.value.isSaveEnabled)

            vm.onSaveClicked()
            advanceUntilIdle()

            assertNotNull(vm.uiState.value.savedPlanId)
            assertNull(vm.uiState.value.submitError)
        }

    @Test
    fun `saving a plan with target warmup sets persists the value and loading it back populates the field`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val vm = viewModel()
            advanceUntilIdle()

            vm.onNameChanged("Push Pull Legs")
            vm.onAddRowClicked()
            val rowId =
                vm.uiState.value.rows
                    .single()
                    .rowId
            vm.onRowExerciseSelected(rowId, exerciseId.value)
            vm.onRowTargetSetsChanged(rowId, "3")
            vm.onRowRepMinChanged(rowId, "8")
            vm.onRowRepMaxChanged(rowId, "12")
            vm.onRowTargetWarmupSetsChanged(rowId, "2")
            assertTrue(vm.uiState.value.isSaveEnabled)

            vm.onSaveClicked()
            advanceUntilIdle()
            val planId = requireNotNull(vm.uiState.value.savedPlanId)

            val editVm = viewModel(planId = planId.value)
            advanceUntilIdle()
            assertEquals(
                "2",
                editVm.uiState.value.rows
                    .single()
                    .targetWarmupSetsText,
            )
        }

    @Test
    fun `a duplicate plan name reports the DUPLICATE_NAME submit error`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
            val existing =
                createTrainingPlan(
                    CreateTrainingPlanCommand(
                        name = "Push Pull Legs",
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
                                    restSeconds = null,
                                    isOptional = false,
                                ),
                            ),
                    ),
                )
            check(existing is DomainResult.Success)

            val vm = viewModel()
            advanceUntilIdle()

            vm.onNameChanged("Push Pull Legs")
            vm.onAddRowClicked()
            val rowId =
                vm.uiState.value.rows
                    .single()
                    .rowId
            vm.onRowExerciseSelected(rowId, exerciseId.value)
            vm.onRowTargetSetsChanged(rowId, "3")
            vm.onRowRepMinChanged(rowId, "8")
            vm.onRowRepMaxChanged(rowId, "12")

            vm.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME,
                vm.uiState.value.submitError
                    ?.kind,
            )
        }

    @Test
    fun `loading an existing plan populates its name and rows from the latest version`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exerciseId = seedExercise()
            val createTrainingPlan = CreateTrainingPlan(planRepository, exerciseRepository, clock, ids)
            val created =
                createTrainingPlan(
                    CreateTrainingPlanCommand(
                        name = "Push Pull Legs",
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
                    ),
                )
            check(created is DomainResult.Success)
            val planId = created.value

            val vm = viewModel(planId = planId.value)
            advanceUntilIdle()

            assertEquals(TrainingPlanEditorLoadStatus.READY, vm.uiState.value.loadStatus)
            assertEquals("Push Pull Legs", vm.uiState.value.name)
            assertEquals(1, vm.uiState.value.rows.size)
            assertEquals(
                exerciseId.value,
                vm.uiState.value.rows
                    .single()
                    .exerciseId,
            )
        }

    @Test
    fun `loading an unknown plan id reports NOT_FOUND`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val vm = viewModel(planId = "missing-id")
            advanceUntilIdle()

            assertEquals(TrainingPlanEditorLoadStatus.NOT_FOUND, vm.uiState.value.loadStatus)
        }
}
