package com.repflow.app.presentation.exercise.editor

import androidx.lifecycle.SavedStateHandle
import com.repflow.app.application.exercise.CreateExercise
import com.repflow.app.application.exercise.ExerciseQueryCriteria
import com.repflow.app.application.exercise.FixedClock
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.InMemoryExerciseRepository
import com.repflow.app.application.exercise.SequentialIdentifierGenerator
import com.repflow.app.application.exercise.UpdateExercise
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseOrigin
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError
import com.repflow.app.domain.exercise.LoadIncrement
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.presentation.navigation.RepFlowDestinations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseEditorViewModelTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = FixedClock(now)
    private val ids = SequentialIdentifierGenerator()
    private val repository = InMemoryExerciseRepository()

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        exerciseId: String? = null,
    ): ExerciseEditorViewModel {
        exerciseId?.let { savedStateHandle[RepFlowDestinations.EXERCISE_EDIT_ARG] = it }
        return ExerciseEditorViewModel(
            savedStateHandle = savedStateHandle,
            createExercise = CreateExercise(repository, clock, ids),
            updateExercise = UpdateExercise(repository, clock),
            getExercise = GetExercise(repository),
        )
    }

    private fun seedExercise(
        id: String = "exercise-seed",
        name: String = "Bench Press",
        trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
        restSeconds: Long? = 90,
        loadIncrementGrams: Long? = 2_500,
    ): Exercise {
        val exercise =
            requireSuccess(
                Exercise.create(
                    id = ExerciseId(id),
                    name = requireSuccess(ExerciseName.create(name)),
                    trackingType = trackingType,
                    instructions = null,
                    defaultLoadIncrement = loadIncrementGrams?.let { requireSuccess(LoadIncrement.create(it)) },
                    defaultRestDuration = restSeconds?.let { requireSuccess(RestDuration.create(it)) },
                    origin = ExerciseOrigin.CUSTOM,
                    createdAt = now,
                ),
            )
        repository.seed(exercise)
        return exercise
    }

    private fun <T, E> requireSuccess(result: DomainResult<T, E>): T = (result as DomainResult.Success).value

    @Test
    fun `create mode starts ready with save disabled until a name is entered`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        assertEquals(ExerciseEditorMode.Create, viewModel.uiState.value.mode)
        assertEquals(ExerciseEditorLoadStatus.READY, viewModel.uiState.value.loadStatus)
        assertFalse(viewModel.uiState.value.isSaveEnabled)

        viewModel.onNameChanged("Squat")

        assertTrue(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun `blank name reports a domain NameBlank error`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onNameChanged("   ")

        assertEquals(
            ExerciseEditorFieldError.Domain(ExerciseValidationError.NameBlank),
            viewModel.uiState.value.nameError,
        )
        assertFalse(viewModel.uiState.value.isSaveEnabled)
    }

    @Test
    fun `an unparsable rest duration reports InvalidNumber, not a domain error`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onRestSecondsChanged("not-a-number")

        assertEquals(ExerciseEditorFieldError.InvalidNumber, viewModel.uiState.value.restDurationError)
    }

    @Test
    fun `a rest duration outside the domain range reports the domain error`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onRestSecondsChanged("999999")

        assertEquals(
            ExerciseEditorFieldError.Domain(ExerciseValidationError.RestDurationOutOfRange),
            viewModel.uiState.value.restDurationError,
        )
    }

    @Test
    fun `switching to a tracking type that cannot carry load clears it and queues a message`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onLoadIncrementChanged("2.5")
        viewModel.onTrackingTypeChanged(ExerciseTrackingType.REPS_ONLY)

        assertEquals("", viewModel.uiState.value.loadIncrementKgText)
        assertEquals(1, viewModel.uiState.value.messages.size)
        assertEquals(
            ExerciseEditorMessage.Kind.LOAD_INCREMENT_CLEARED,
            viewModel.uiState.value.messages
                .first()
                .kind,
        )

        val messageId =
            viewModel.uiState.value.messages
                .first()
                .id
        viewModel.onMessageShown(messageId)

        assertTrue(
            viewModel.uiState.value.messages
                .isEmpty(),
        )
    }

    @Test
    fun `switching tracking type does not clear an already-blank load increment or queue a message`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onTrackingTypeChanged(ExerciseTrackingType.REPS_ONLY)

        assertTrue(
            viewModel.uiState.value.messages
                .isEmpty(),
        )
    }

    @Test
    fun `saving a valid create draft persists the exercise and sets savedExerciseId`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val viewModel = viewModel()
            viewModel.onNameChanged("Deadlift")

            viewModel.onSaveClicked()
            advanceUntilIdle()

            val savedId = viewModel.uiState.value.savedExerciseId
            requireNotNull(savedId)
            val stored = repository.findById(savedId)
            requireNotNull(stored)
            assertEquals("Deadlift", stored.name.value)
        }

    @Test
    fun `saving a duplicate name surfaces a DUPLICATE_NAME submit error`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            seedExercise(name = "Deadlift")
            val viewModel = viewModel()
            viewModel.onNameChanged("Deadlift")

            viewModel.onSaveClicked()
            advanceUntilIdle()

            assertEquals(
                ExerciseEditorSubmitErrorKind.DUPLICATE_NAME,
                viewModel.uiState.value.submitError
                    ?.kind,
            )
            assertNull(viewModel.uiState.value.savedExerciseId)
        }

    @Test
    fun `isSaving prevents a second concurrent submission`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            val viewModel = viewModel()
            viewModel.onNameChanged("Overhead Press")

            viewModel.onSaveClicked()
            assertTrue(viewModel.uiState.value.isSaving)

            // Attempting a second save while the first is still in flight must be a no-op:
            // isSaveEnabled is false while isSaving is true (implementation correction 10).
            viewModel.onSaveClicked()
            advanceUntilIdle()

            val storedCount = repository.observe(ExerciseQueryCriteria()).first().size
            assertEquals(1, storedCount)
        }

    @Test
    fun `edit mode loads the existing exercise into the draft`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exercise = seedExercise()
            val viewModel = viewModel(exerciseId = exercise.id.value)
            advanceUntilIdle()

            assertEquals(ExerciseEditorLoadStatus.READY, viewModel.uiState.value.loadStatus)
            assertEquals(exercise.name.value, viewModel.uiState.value.name)
            assertEquals(exercise.trackingType, viewModel.uiState.value.trackingType)
            assertEquals("90", viewModel.uiState.value.restSecondsText)
            assertEquals("2.5", viewModel.uiState.value.loadIncrementKgText)
        }

    @Test
    fun `editing an unknown exercise id reports NOT_FOUND`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val viewModel = viewModel(exerciseId = "missing-id")
            advanceUntilIdle()

            assertEquals(ExerciseEditorLoadStatus.NOT_FOUND, viewModel.uiState.value.loadStatus)
        }

    @Test
    fun `saving an unchanged edit draft performs no write`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val exercise = seedExercise()
            val viewModel = viewModel(exerciseId = exercise.id.value)
            advanceUntilIdle()

            viewModel.onSaveClicked()
            advanceUntilIdle()

            val stored = repository.findById(exercise.id)
            requireNotNull(stored)
            assertEquals(exercise.updatedAt, stored.updatedAt)
            assertEquals(exercise.id, viewModel.uiState.value.savedExerciseId)
        }

    @Test
    fun `back with no changes dismisses immediately without a discard dialog`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()

        viewModel.onBackRequested()

        assertTrue(viewModel.uiState.value.dismissed)
        assertFalse(viewModel.uiState.value.isDiscardDialogVisible)
    }

    @Test
    fun `back with unsaved changes shows the discard dialog instead of dismissing`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()
        viewModel.onNameChanged("Squat")

        viewModel.onBackRequested()

        assertTrue(viewModel.uiState.value.isDiscardDialogVisible)
        assertFalse(viewModel.uiState.value.dismissed)

        viewModel.onDiscardConfirmed()

        assertTrue(viewModel.uiState.value.dismissed)
        assertFalse(viewModel.uiState.value.isDiscardDialogVisible)
    }

    @Test
    fun `discard cancelled keeps the draft and hides the dialog`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val viewModel = viewModel()
        viewModel.onNameChanged("Squat")
        viewModel.onBackRequested()

        viewModel.onDiscardCancelled()

        assertFalse(viewModel.uiState.value.isDiscardDialogVisible)
        assertFalse(viewModel.uiState.value.dismissed)
        assertEquals("Squat", viewModel.uiState.value.name)
    }

    @Test
    fun `a draft survives recreation from the same SavedStateHandle (D-22)`() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val savedStateHandle = SavedStateHandle()
        val first = viewModel(savedStateHandle = savedStateHandle)
        first.onNameChanged("Romanian Deadlift")
        first.onRestSecondsChanged("120")

        // Simulate Activity/process recreation: a brand-new ViewModel instance built
        // from the same (Android-restored) SavedStateHandle contents.
        val recreated = viewModel(savedStateHandle = savedStateHandle)

        assertEquals("Romanian Deadlift", recreated.uiState.value.name)
        assertEquals("120", recreated.uiState.value.restSecondsText)
    }
}
