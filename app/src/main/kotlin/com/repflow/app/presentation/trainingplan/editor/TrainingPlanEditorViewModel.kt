package com.repflow.app.presentation.trainingplan.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.repflow.app.application.exercise.ExerciseStatusFilter
import com.repflow.app.application.exercise.ObserveExercises
import com.repflow.app.application.trainingplan.CreateTrainingPlan
import com.repflow.app.application.trainingplan.CreateTrainingPlanCommand
import com.repflow.app.application.trainingplan.GetTrainingPlanDetail
import com.repflow.app.application.trainingplan.PlannedExerciseInput
import com.repflow.app.application.trainingplan.PlannedExerciseTargetKind
import com.repflow.app.application.trainingplan.ReviseTrainingPlan
import com.repflow.app.application.trainingplan.ReviseTrainingPlanCommand
import com.repflow.app.application.trainingplan.TrainingPlanOperationError
import com.repflow.app.application.trainingplan.TrainingPlanOverview
import com.repflow.app.domain.common.DomainResult
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.RestDuration
import com.repflow.app.domain.trainingplan.DurationTarget
import com.repflow.app.domain.trainingplan.PlannedExercise
import com.repflow.app.domain.trainingplan.PlannedExerciseTarget
import com.repflow.app.domain.trainingplan.RepRange
import com.repflow.app.domain.trainingplan.TargetSets
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanName
import com.repflow.app.presentation.navigation.RepFlowDestinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [TrainingPlanEditorUiState] for both "create" and "edit" routes,
 * mirroring
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorViewModel]'s
 * shape. See that class and the reference doc for the rationale behind
 * keeping [rows][TrainingPlanEditorUiState.rows] purely in-memory while
 * [name][TrainingPlanEditorUiState.name] round-trips through
 * [savedStateHandle].
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class TrainingPlanEditorViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val createTrainingPlan: CreateTrainingPlan,
        private val reviseTrainingPlan: ReviseTrainingPlan,
        private val getTrainingPlanDetail: GetTrainingPlanDetail,
        observeExercises: ObserveExercises,
    ) : ViewModel() {
        private val editingId: TrainingPlanId? =
            savedStateHandle.get<String>(RepFlowDestinations.PLAN_EDIT_ARG)?.let(::TrainingPlanId)

        private var loadedOverview: TrainingPlanOverview? = null
        private var nextRowId = 0L

        private val _uiState =
            MutableStateFlow(
                revalidate(
                    TrainingPlanEditorUiState(
                        mode = editingId?.let(TrainingPlanEditorMode::Edit) ?: TrainingPlanEditorMode.Create,
                        loadStatus = if (editingId != null) TrainingPlanEditorLoadStatus.LOADING else TrainingPlanEditorLoadStatus.READY,
                        name = savedStateHandle[KEY_NAME] ?: "",
                    ),
                ),
            )
        val uiState: StateFlow<TrainingPlanEditorUiState> = _uiState.asStateFlow()

        init {
            observeExercises(ExerciseStatusFilter.ACTIVE, "")
                .onEach { exercises ->
                    val options = exercises.map { TrainingPlanEditorExerciseOption(it.id.value, it.name.value, it.trackingType) }
                    _uiState.update { revalidate(it.copy(availableExercises = options)) }
                }.launchIn(viewModelScope)
            editingId?.let(::loadExisting)
        }

        private fun loadExisting(id: TrainingPlanId) {
            viewModelScope.launch {
                when (val result = getTrainingPlanDetail(id)) {
                    is DomainResult.Success -> onExistingLoaded(result.value)
                    is DomainResult.Failure -> _uiState.update { it.copy(loadStatus = TrainingPlanEditorLoadStatus.NOT_FOUND) }
                }
            }
        }

        private fun onExistingLoaded(overview: TrainingPlanOverview) {
            loadedOverview = overview
            if (savedStateHandle.get<Boolean>(KEY_DRAFT_INITIALIZED) != true) {
                savedStateHandle[KEY_NAME] = overview.plan.name.value
                savedStateHandle[KEY_DRAFT_INITIALIZED] = true
            }
            _uiState.update { state ->
                revalidate(
                    state.copy(
                        loadStatus = TrainingPlanEditorLoadStatus.READY,
                        name = savedStateHandle[KEY_NAME] ?: state.name,
                        rows = state.rows.ifEmpty { overview.latestVersion.plannedExercises.map(::toRow) },
                    ),
                )
            }
        }

        private fun toRow(exercise: PlannedExercise) =
            PlannedExerciseRowUiState(
                rowId = nextRowId++,
                exerciseId = exercise.exerciseId.value,
                targetSetsText = exercise.targetSets.value.toString(),
                repMinText = (exercise.target as? PlannedExerciseTarget.Reps)?.range?.min?.toString() ?: "",
                repMaxText = (exercise.target as? PlannedExerciseTarget.Reps)?.range?.max?.toString() ?: "",
                durationMinText = (exercise.target as? PlannedExerciseTarget.Duration)?.range?.minSeconds?.toString() ?: "",
                durationMaxText = (exercise.target as? PlannedExerciseTarget.Duration)?.range?.maxSeconds?.toString() ?: "",
                restSecondsText = exercise.restDuration?.seconds?.toString() ?: "",
                isOptional = exercise.isOptional,
            )

        fun onNameChanged(value: String) {
            savedStateHandle[KEY_NAME] = value
            _uiState.update { revalidate(it.copy(name = value)) }
        }

        fun onAddRowClicked() {
            _uiState.update { state ->
                revalidate(state.copy(rows = state.rows + PlannedExerciseRowUiState(rowId = nextRowId++)))
            }
        }

        fun onRemoveRowClicked(rowId: Long) {
            _uiState.update { state -> revalidate(state.copy(rows = state.rows.filterNot { it.rowId == rowId })) }
        }

        fun onMoveRowUp(rowId: Long) = moveRow(rowId, offset = -1)

        fun onMoveRowDown(rowId: Long) = moveRow(rowId, offset = 1)

        private fun moveRow(
            rowId: Long,
            offset: Int,
        ) {
            _uiState.update { state ->
                val index = state.rows.indexOfFirst { it.rowId == rowId }
                val targetIndex = index + offset
                if (index < 0 || targetIndex < 0 || targetIndex >= state.rows.size) return@update state
                val rows = state.rows.toMutableList()
                val moved = rows.removeAt(index)
                rows.add(targetIndex, moved)
                revalidate(state.copy(rows = rows))
            }
        }

        fun onRowExerciseSelected(
            rowId: Long,
            exerciseId: String,
        ) {
            _uiState.update { state ->
                val option = state.availableExercises.firstOrNull { it.id == exerciseId }
                revalidate(state.copy(rows = state.rows.map { row -> if (row.rowId == rowId) applyExercise(row, option) else row }))
            }
        }

        private fun applyExercise(
            row: PlannedExerciseRowUiState,
            option: TrainingPlanEditorExerciseOption?,
        ): PlannedExerciseRowUiState =
            if (option == null) {
                row.copy(exerciseId = null, exerciseName = "", trackingType = null)
            } else {
                row.copy(exerciseId = option.id, exerciseName = option.name, trackingType = option.trackingType)
            }

        fun onRowTargetSetsChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(targetSetsText = value) }

        fun onRowRepMinChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(repMinText = value) }

        fun onRowRepMaxChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(repMaxText = value) }

        fun onRowDurationMinChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(durationMinText = value) }

        fun onRowDurationMaxChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(durationMaxText = value) }

        fun onRowRestSecondsChanged(
            rowId: Long,
            value: String,
        ) = updateRow(rowId) { it.copy(restSecondsText = value) }

        fun onRowOptionalChanged(
            rowId: Long,
            value: Boolean,
        ) = updateRow(rowId) { it.copy(isOptional = value) }

        private fun updateRow(
            rowId: Long,
            transform: (PlannedExerciseRowUiState) -> PlannedExerciseRowUiState,
        ) {
            _uiState.update { state ->
                revalidate(state.copy(rows = state.rows.map { row -> if (row.rowId == rowId) transform(row) else row }))
            }
        }

        fun onSaveNavigationHandled() {
            _uiState.update { it.copy(savedPlanId = null) }
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
                when (val result = submit(state)) {
                    is DomainResult.Success -> _uiState.update { it.copy(isSaving = false, savedPlanId = result.value) }
                    is DomainResult.Failure -> _uiState.update { it.copy(isSaving = false, submitError = toSubmitError(result.error)) }
                }
            }
        }

        private suspend fun submit(state: TrainingPlanEditorUiState): DomainResult<TrainingPlanId, TrainingPlanOperationError> =
            when (val mode = state.mode) {
                is TrainingPlanEditorMode.Create -> createTrainingPlan(toCreateCommand(state))
                is TrainingPlanEditorMode.Edit -> submitEdit(mode.planId, state)
            }

        private suspend fun submitEdit(
            id: TrainingPlanId,
            state: TrainingPlanEditorUiState,
        ): DomainResult<TrainingPlanId, TrainingPlanOperationError> =
            when (val result = reviseTrainingPlan(toReviseCommand(id, state))) {
                is DomainResult.Success -> DomainResult.Success(id)
                is DomainResult.Failure -> DomainResult.Failure(result.error)
            }

        private fun toCreateCommand(state: TrainingPlanEditorUiState) =
            CreateTrainingPlanCommand(name = state.name, plannedExercises = state.rows.mapIndexed(::toInput))

        private fun toReviseCommand(
            id: TrainingPlanId,
            state: TrainingPlanEditorUiState,
        ) = ReviseTrainingPlanCommand(planId = id.value, name = state.name, plannedExercises = state.rows.mapIndexed(::toInput))

        private fun toInput(
            index: Int,
            row: PlannedExerciseRowUiState,
        ) = PlannedExerciseInput(
            exerciseId = row.exerciseId.orEmpty(),
            order = index,
            targetSets = row.targetSetsText.toIntOrNull() ?: 0,
            targetKind =
                if (row.trackingType ==
                    ExerciseTrackingType.DURATION
                ) {
                    PlannedExerciseTargetKind.DURATION
                } else {
                    PlannedExerciseTargetKind.REPS
                },
            repMin = row.repMinText.toIntOrNull(),
            repMax = row.repMaxText.toIntOrNull(),
            durationMinSeconds = row.durationMinText.toLongOrNull(),
            durationMaxSeconds = row.durationMaxText.toLongOrNull(),
            restSeconds = row.restSecondsText.toLongOrNull(),
            isOptional = row.isOptional,
        )

        private fun isDirty(state: TrainingPlanEditorUiState): Boolean =
            when (state.mode) {
                is TrainingPlanEditorMode.Create -> state.name.isNotBlank() || state.rows.isNotEmpty()
                is TrainingPlanEditorMode.Edit -> loadedOverview != null
            }

        private fun toSubmitError(error: TrainingPlanOperationError): TrainingPlanEditorSubmitError =
            when (error) {
                TrainingPlanOperationError.DuplicateName -> {
                    TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.DUPLICATE_NAME)
                }

                is TrainingPlanOperationError.ValidationFailed, is TrainingPlanOperationError.PlannedExerciseInvalid -> {
                    TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.INVALID)
                }

                else -> {
                    TrainingPlanEditorSubmitError(TrainingPlanEditorSubmitErrorKind.UNAVAILABLE)
                }
            }

        private fun revalidate(state: TrainingPlanEditorUiState): TrainingPlanEditorUiState =
            state.copy(
                nameError = validateName(state.name),
                rows = state.rows.map { backfillExerciseDetails(it, state.availableExercises) }.map(::revalidateRow),
                rowsError = if (state.rows.isEmpty()) TrainingPlanEditorFieldError.Required else null,
            )

        /**
         * Rows loaded from an existing plan (see [toRow]) only carry an
         * `exerciseId`; the display name and tracking type are resolved here
         * once the matching option is available, since exercises load via a
         * separate flow that may not have emitted yet when the plan itself
         * finishes loading.
         */
        private fun backfillExerciseDetails(
            row: PlannedExerciseRowUiState,
            availableExercises: List<TrainingPlanEditorExerciseOption>,
        ): PlannedExerciseRowUiState {
            if (row.exerciseId == null || row.exerciseName.isNotEmpty()) return row
            val option = availableExercises.firstOrNull { it.id == row.exerciseId } ?: return row
            return row.copy(exerciseName = option.name, trackingType = option.trackingType)
        }

        private fun validateName(name: String): TrainingPlanEditorFieldError? =
            (TrainingPlanName.create(name) as? DomainResult.Failure)?.error?.let(TrainingPlanEditorFieldError::Domain)

        private fun revalidateRow(row: PlannedExerciseRowUiState): PlannedExerciseRowUiState =
            row.copy(
                exerciseError = if (row.exerciseId == null) TrainingPlanEditorFieldError.Required else null,
                targetSetsError = validateTargetSets(row.targetSetsText),
                targetRangeError = validateTargetRange(row),
                restError = validateRest(row.restSecondsText),
            )

        private fun validateTargetSets(text: String): TrainingPlanEditorFieldError? {
            if (text.isBlank()) return TrainingPlanEditorFieldError.Required
            val value = text.toIntOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            return (TargetSets.create(value) as? DomainResult.Failure)?.error?.let(TrainingPlanEditorFieldError::Domain)
        }

        private fun validateTargetRange(row: PlannedExerciseRowUiState): TrainingPlanEditorFieldError? =
            when (row.trackingType) {
                ExerciseTrackingType.DURATION -> validateDurationRange(row)
                ExerciseTrackingType.WEIGHT_AND_REPS, ExerciseTrackingType.REPS_ONLY, null -> validateRepRange(row)
            }

        private fun validateRepRange(row: PlannedExerciseRowUiState): TrainingPlanEditorFieldError? {
            if (row.repMinText.isBlank() || row.repMaxText.isBlank()) return TrainingPlanEditorFieldError.Required
            val min = row.repMinText.toIntOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            val max = row.repMaxText.toIntOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            return (RepRange.create(min, max) as? DomainResult.Failure)?.error?.let(TrainingPlanEditorFieldError::Domain)
        }

        private fun validateDurationRange(row: PlannedExerciseRowUiState): TrainingPlanEditorFieldError? {
            if (row.durationMinText.isBlank() || row.durationMaxText.isBlank()) return TrainingPlanEditorFieldError.Required
            val min = row.durationMinText.toLongOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            val max = row.durationMaxText.toLongOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            return (DurationTarget.create(min, max) as? DomainResult.Failure)?.error?.let(TrainingPlanEditorFieldError::Domain)
        }

        private fun validateRest(text: String): TrainingPlanEditorFieldError? {
            if (text.isBlank()) return null
            val seconds = text.toLongOrNull() ?: return TrainingPlanEditorFieldError.InvalidNumber
            return (RestDuration.create(seconds) as? DomainResult.Failure)?.let { TrainingPlanEditorFieldError.InvalidNumber }
        }

        private companion object {
            const val KEY_NAME = "draft_name"
            const val KEY_DRAFT_INITIALIZED = "draft_initialized_from_plan"
        }
    }
