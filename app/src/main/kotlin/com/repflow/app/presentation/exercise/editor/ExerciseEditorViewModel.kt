package com.repflow.app.presentation.exercise.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.CreateExercise
import com.repflow.app.application.exercise.CreateExerciseCommand
import com.repflow.app.application.exercise.ExerciseOperationError
import com.repflow.app.application.exercise.GetExercise
import com.repflow.app.application.exercise.UpdateExercise
import com.repflow.app.application.exercise.UpdateExerciseCommand
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.Exercise
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseInstructions
import com.repflow.app.domain.exercise.ExerciseName
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.LoadIncrement
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.presentation.exercise.ExerciseUiFormatting
import com.repflow.app.presentation.navigation.RepFlowDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [ExerciseEditorUiState] for both the "create" and "edit" routes
 * (see plan.md section H and checkpoint 9).
 *
 * Only primitive/string draft values and the `exerciseId` route argument are
 * read from and written to [savedStateHandle] - never a domain [Exercise]
 * instance (D-22). The supported recovery claim is limited to
 * Activity/process recreation while Android restores saved state; it is
 * **not** claimed after force-stop, clear-data or uninstall.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class ExerciseEditorViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val createExercise: CreateExercise,
        private val updateExercise: UpdateExercise,
        private val getExercise: GetExercise,
    ) : ViewModel() {
        private val editingId: ExerciseId? =
            savedStateHandle.get<String>(RepFlowDestinations.EXERCISE_EDIT_ARG)?.let(::ExerciseId)

        /**
         * The create route's optional name prefill - the library's `Create
         * "<query>"` (remediation-1 CP10). It only seeds the draft: a restored
         * draft name always wins (D-22), it never decides the mode, and an
         * untouched prefill is not a change ([isDirty]). `""` when absent and
         * in edit mode.
         */
        private val prefillName: String =
            if (editingId == null) {
                savedStateHandle.get<String>(RepFlowDestinations.EXERCISE_NEW_NAME_ARG).orEmpty()
            } else {
                ""
            }

        private var loadedExercise: Exercise? = null
        private var nextMessageId = 0L
        private var refusedSaves = 0

        private val _uiState = MutableStateFlow(revalidate(initialState(editingId)))
        val uiState: StateFlow<ExerciseEditorUiState> = _uiState.asStateFlow()

        init {
            editingId?.let(::loadExisting)
        }

        private fun initialState(id: ExerciseId?): ExerciseEditorUiState =
            ExerciseEditorUiState(
                mode = if (id != null) ExerciseEditorMode.Edit(id) else ExerciseEditorMode.Create,
                loadStatus = if (id != null) ExerciseEditorLoadStatus.LOADING else ExerciseEditorLoadStatus.READY,
                name = (savedStateHandle[KEY_NAME] ?: prefillName),
                nameTouched = (savedStateHandle.get<String>(KEY_NAME) ?: prefillName).isNotEmpty(),
                trackingType = restoredTrackingType(),
                instructions = savedStateHandle[KEY_INSTRUCTIONS] ?: "",
                restSecondsText = savedStateHandle[KEY_REST_SECONDS] ?: "",
                loadIncrementKgText = savedStateHandle[KEY_LOAD_INCREMENT] ?: "",
            )

        private fun restoredTrackingType(): ExerciseTrackingType {
            val raw = savedStateHandle.get<String>(KEY_TRACKING_TYPE) ?: return ExerciseTrackingType.WEIGHT_AND_REPS
            return ExerciseTrackingType.entries.firstOrNull { it.name == raw } ?: ExerciseTrackingType.WEIGHT_AND_REPS
        }

        private fun loadExisting(id: ExerciseId) {
            viewModelScope.launch {
                when (val result = getExercise(id)) {
                    is DomainResult.Success -> {
                        onExistingLoaded(result.value)
                    }

                    is DomainResult.Failure -> {
                        _uiState.update { it.copy(loadStatus = ExerciseEditorLoadStatus.NOT_FOUND) }
                    }
                }
            }
        }

        private fun onExistingLoaded(exercise: Exercise) {
            loadedExercise = exercise
            if (savedStateHandle.get<Boolean>(KEY_DRAFT_INITIALIZED) != true) {
                populateDraftFrom(exercise)
                savedStateHandle[KEY_DRAFT_INITIALIZED] = true
            }
            _uiState.update { state ->
                revalidate(
                    state.copy(
                        loadStatus = ExerciseEditorLoadStatus.READY,
                        name = savedStateHandle[KEY_NAME] ?: state.name,
                        trackingType = restoredTrackingType(),
                        instructions = savedStateHandle[KEY_INSTRUCTIONS] ?: state.instructions,
                        restSecondsText = savedStateHandle[KEY_REST_SECONDS] ?: state.restSecondsText,
                        loadIncrementKgText = savedStateHandle[KEY_LOAD_INCREMENT] ?: state.loadIncrementKgText,
                    ),
                )
            }
        }

        private fun populateDraftFrom(exercise: Exercise) {
            savedStateHandle[KEY_NAME] = exercise.name.value
            savedStateHandle[KEY_TRACKING_TYPE] = exercise.trackingType.name
            savedStateHandle[KEY_INSTRUCTIONS] = exercise.instructions?.value ?: ""
            savedStateHandle[KEY_REST_SECONDS] = exercise.defaultRestDuration?.seconds?.toString() ?: ""
            savedStateHandle[KEY_LOAD_INCREMENT] =
                exercise.defaultLoadIncrement?.grams?.let(ExerciseUiFormatting::gramsToKgText) ?: ""
        }

        /** The name field lost focus: it now counts as touched, so an empty name shows its error (functional review R2-F-1). */
        fun onNameFocusLost() {
            _uiState.update { it.copy(nameTouched = true) }
        }

        fun onNameChanged(value: String) {
            savedStateHandle[KEY_NAME] = value
            _uiState.update { state ->
                // Editing the name answers a duplicate-name refusal; `Save` checks it again (functional review R3-F-5).
                val submitError = state.submitError?.takeUnless { it.kind == ExerciseEditorSubmitErrorKind.DUPLICATE_NAME }
                revalidate(state.copy(name = value, nameTouched = true, submitError = submitError))
            }
        }

        fun onInstructionsChanged(value: String) {
            savedStateHandle[KEY_INSTRUCTIONS] = value
            _uiState.update { revalidate(it.copy(instructions = value)) }
        }

        fun onRestSecondsChanged(value: String) {
            savedStateHandle[KEY_REST_SECONDS] = value
            _uiState.update { revalidate(it.copy(restSecondsText = value)) }
        }

        fun onLoadIncrementChanged(value: String) {
            savedStateHandle[KEY_LOAD_INCREMENT] = value
            _uiState.update { revalidate(it.copy(loadIncrementKgText = value)) }
        }

        /**
         * Switching to a tracking type that cannot carry load clears the
         * load increment with a visible hint, never silently (implementation
         * correction 12 / plan.md section B).
         */
        fun onTrackingTypeChanged(trackingType: ExerciseTrackingType) {
            savedStateHandle[KEY_TRACKING_TYPE] = trackingType.name
            _uiState.update { state ->
                val shouldClearLoad = !trackingType.supportsLoad && state.loadIncrementKgText.isNotBlank()
                if (shouldClearLoad) savedStateHandle[KEY_LOAD_INCREMENT] = ""
                val messages =
                    if (shouldClearLoad) {
                        state.messages + ExerciseEditorMessage(nextMessageId++, ExerciseEditorMessage.Kind.LOAD_INCREMENT_CLEARED)
                    } else {
                        state.messages
                    }
                revalidate(
                    state.copy(
                        trackingType = trackingType,
                        loadIncrementKgText = if (shouldClearLoad) "" else state.loadIncrementKgText,
                        messages = messages,
                    ),
                )
            }
        }

        fun onMessageShown(messageId: Long) {
            _uiState.update { state -> state.copy(messages = state.messages.filterNot { it.id == messageId }) }
        }

        fun onSaveNavigationHandled() {
            _uiState.update { it.copy(savedExerciseId = null) }
        }

        fun onBackRequested() {
            val state = _uiState.value
            if (isDirty(state)) {
                _uiState.update { it.copy(isDiscardDialogVisible = true) }
            } else {
                _uiState.update { it.copy(dismissed = true) }
            }
        }

        fun onDiscardConfirmed() {
            _uiState.update { it.copy(isDiscardDialogVisible = false, dismissed = true) }
        }

        fun onDiscardCancelled() {
            _uiState.update { it.copy(isDiscardDialogVisible = false) }
        }

        fun onDismissHandled() {
            _uiState.update { it.copy(dismissed = false) }
        }

        fun onSaveClicked() {
            val state = _uiState.value
            if (!state.isSaveEnabled) return
            _uiState.update { it.copy(isSaving = true, submitError = null) }
            viewModelScope.launch {
                val result = submit(state)
                when (result) {
                    is DomainResult.Success -> {
                        _uiState.update { it.copy(isSaving = false, savedExerciseId = result.value) }
                    }

                    is DomainResult.Failure -> {
                        _uiState.update { it.copy(isSaving = false, submitError = toSubmitError(result.error)) }
                    }
                }
            }
        }

        private suspend fun submit(state: ExerciseEditorUiState): DomainResult<ExerciseId, ExerciseOperationError> =
            when (val mode = state.mode) {
                is ExerciseEditorMode.Create -> createExercise(toCreateCommand(state))
                is ExerciseEditorMode.Edit -> submitEdit(mode.exerciseId, state)
            }

        /** An unchanged draft performs no write and does not bump `updatedAt` (implementation correction 9). */
        private suspend fun submitEdit(
            id: ExerciseId,
            state: ExerciseEditorUiState,
        ): DomainResult<ExerciseId, ExerciseOperationError> {
            if (isUnchangedFromLoaded(state)) {
                return DomainResult.Success(id)
            }
            return when (val result = updateExercise(toUpdateCommand(id, state))) {
                is DomainResult.Success -> DomainResult.Success(id)
                is DomainResult.Failure -> DomainResult.Failure(result.error)
            }
        }

        private fun toCreateCommand(state: ExerciseEditorUiState) =
            CreateExerciseCommand(
                name = state.name,
                trackingType = state.trackingType,
                instructions = state.instructions.ifBlank { null },
                defaultLoadIncrementGrams = loadIncrementGrams(state),
                defaultRestSeconds = state.restSecondsText.toLongOrNull(),
            )

        private fun toUpdateCommand(
            id: ExerciseId,
            state: ExerciseEditorUiState,
        ) = UpdateExerciseCommand(
            id = id,
            name = state.name,
            trackingType = state.trackingType,
            instructions = state.instructions.ifBlank { null },
            defaultLoadIncrementGrams = loadIncrementGrams(state),
            defaultRestSeconds = state.restSecondsText.toLongOrNull(),
        )

        private fun loadIncrementGrams(state: ExerciseEditorUiState): Long? =
            if (state.trackingType.supportsLoad) ExerciseUiFormatting.kgTextToGrams(state.loadIncrementKgText) else null

        private fun isUnchangedFromLoaded(state: ExerciseEditorUiState): Boolean {
            val loaded = loadedExercise ?: return false
            return ExerciseName.cleanDisplay(state.name) == loaded.name.value &&
                state.trackingType == loaded.trackingType &&
                state.instructions.trim().ifEmpty { null } == loaded.instructions?.value &&
                state.restSecondsText.toLongOrNull() == loaded.defaultRestDuration?.seconds &&
                loadIncrementGrams(state) == loaded.defaultLoadIncrement?.grams
        }

        private fun isDirty(state: ExerciseEditorUiState): Boolean =
            when (state.mode) {
                is ExerciseEditorMode.Create -> {
                    state.name.trim() != prefillName.trim() ||
                        state.instructions.isNotBlank() ||
                        state.restSecondsText.isNotBlank() ||
                        state.loadIncrementKgText.isNotBlank() ||
                        state.trackingType != ExerciseTrackingType.WEIGHT_AND_REPS
                }

                is ExerciseEditorMode.Edit -> {
                    loadedExercise != null && !isUnchangedFromLoaded(state)
                }
            }

        private fun toSubmitError(error: ExerciseOperationError): ExerciseEditorSubmitError =
            when (error) {
                ExerciseOperationError.DuplicateName -> {
                    ExerciseEditorSubmitError(ExerciseEditorSubmitErrorKind.DUPLICATE_NAME, ++refusedSaves)
                }

                else -> {
                    ExerciseEditorSubmitError(ExerciseEditorSubmitErrorKind.UNAVAILABLE, ++refusedSaves)
                }
            }

        private fun revalidate(state: ExerciseEditorUiState): ExerciseEditorUiState =
            state.copy(
                nameError = validateName(state.name),
                instructionsError = validateInstructions(state.instructions),
                restDurationError = validateRestDuration(state.restSecondsText),
                loadIncrementError =
                    if (state.trackingType.supportsLoad) validateLoadIncrement(state.loadIncrementKgText) else null,
            )

        private fun validateName(name: String): ExerciseEditorFieldError? =
            (ExerciseName.create(name) as? DomainResult.Failure)?.error?.let(ExerciseEditorFieldError::Domain)

        private fun validateInstructions(instructions: String): ExerciseEditorFieldError? =
            (ExerciseInstructions.createOrNull(instructions) as? DomainResult.Failure)
                ?.error
                ?.let(ExerciseEditorFieldError::Domain)

        private fun validateRestDuration(text: String): ExerciseEditorFieldError? {
            if (text.isBlank()) return null
            val seconds = text.toLongOrNull() ?: return ExerciseEditorFieldError.InvalidNumber
            return (RestDuration.create(seconds) as? DomainResult.Failure)?.error?.let(ExerciseEditorFieldError::Domain)
        }

        private fun validateLoadIncrement(text: String): ExerciseEditorFieldError? {
            if (text.isBlank()) return null
            val grams = ExerciseUiFormatting.kgTextToGrams(text) ?: return ExerciseEditorFieldError.InvalidNumber
            return (LoadIncrement.create(grams) as? DomainResult.Failure)?.error?.let(ExerciseEditorFieldError::Domain)
        }

        private companion object {
            const val KEY_NAME = "draft_name"
            const val KEY_TRACKING_TYPE = "draft_tracking_type"
            const val KEY_INSTRUCTIONS = "draft_instructions"
            const val KEY_REST_SECONDS = "draft_rest_seconds"
            const val KEY_LOAD_INCREMENT = "draft_load_increment_kg"
            const val KEY_DRAFT_INITIALIZED = "draft_initialized_from_exercise"
        }
    }
