package com.repflow.app.presentation.trainingplan.editor

import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.trainingplan.TrainingPlanId
import com.repflow.app.domain.trainingplan.TrainingPlanValidationError

/**
 * Unidirectional editor state, mirroring
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorUiState]'s
 * shape. Per the reference doc's explicit allowance, [rows] is a plain
 * in-memory list - only [name] is persisted through
 * [androidx.lifecycle.SavedStateHandle] (D-22 still applies: even that is a
 * primitive, never a domain object).
 */
data class TrainingPlanEditorUiState(
    val mode: TrainingPlanEditorMode = TrainingPlanEditorMode.Create,
    val loadStatus: TrainingPlanEditorLoadStatus = TrainingPlanEditorLoadStatus.READY,
    val name: String = "",
    val nameError: TrainingPlanEditorFieldError? = null,
    /** Set once the user has typed in the name field (or it arrived non-empty); a pristine form shows no error. */
    val nameTouched: Boolean = false,
    val availableExercises: List<TrainingPlanEditorExerciseOption> = emptyList(),
    val rows: List<PlannedExerciseRowUiState> = emptyList(),
    val rowsError: TrainingPlanEditorFieldError? = null,
    val isSaving: Boolean = false,
    val isDiscardDialogVisible: Boolean = false,
    val savedPlanId: TrainingPlanId? = null,
    val dismissed: Boolean = false,
    val submitError: TrainingPlanEditorSubmitError? = null,
) {
    /** The name's validation error, shown only once the field has been touched. */
    val visibleNameError: TrainingPlanEditorFieldError?
        get() = nameError.takeIf { nameTouched }

    val isSaveEnabled: Boolean
        get() =
            !isSaving &&
                loadStatus == TrainingPlanEditorLoadStatus.READY &&
                name.isNotBlank() &&
                nameError == null &&
                rowsError == null &&
                rows.isNotEmpty() &&
                rows.all { it.hasNoErrors }
}

enum class TrainingPlanEditorLoadStatus {
    LOADING,
    READY,
    NOT_FOUND,
}

/** A selectable row in the exercise picker; a denormalized read-only projection of an [com.repflow.app.domain.exercise.Exercise]. */
data class TrainingPlanEditorExerciseOption(
    val id: String,
    val name: String,
    val trackingType: ExerciseTrackingType,
)

/**
 * One in-memory row of the plan being edited. [rowId] is a presentation-only
 * stable key (never persisted) used for `LazyColumn` item keys and to
 * target move/remove actions; it has no relation to
 * [com.repflow.app.domain.trainingplan.PlannedExerciseId], which is only
 * assigned on save.
 */
data class PlannedExerciseRowUiState(
    val rowId: Long,
    val exerciseId: String? = null,
    val exerciseName: String = "",
    val trackingType: ExerciseTrackingType? = null,
    val targetSetsText: String = "",
    val repMinText: String = "",
    val repMaxText: String = "",
    val durationMinText: String = "",
    val durationMaxText: String = "",
    val restSecondsText: String = "",
    val isOptional: Boolean = false,
    val targetWarmupSetsText: String = "",
    val exerciseError: TrainingPlanEditorFieldError? = null,
    val targetSetsError: TrainingPlanEditorFieldError? = null,
    val targetRangeError: TrainingPlanEditorFieldError? = null,
    val restError: TrainingPlanEditorFieldError? = null,
    val targetWarmupSetsError: TrainingPlanEditorFieldError? = null,
) {
    val hasNoErrors: Boolean
        get() =
            exerciseError == null &&
                targetSetsError == null &&
                targetRangeError == null &&
                restError == null &&
                targetWarmupSetsError == null
}

/**
 * A field's current problem, mirroring
 * [com.repflow.app.presentation.exercise.editor.ExerciseEditorFieldError]:
 * an unparsable raw value ([InvalidNumber], presentation-only) versus a
 * domain invariant violation ([Domain]).
 */
sealed interface TrainingPlanEditorFieldError {
    data class Domain(
        val error: TrainingPlanValidationError,
    ) : TrainingPlanEditorFieldError

    data object InvalidNumber : TrainingPlanEditorFieldError

    data object Required : TrainingPlanEditorFieldError
}

enum class TrainingPlanEditorSubmitErrorKind {
    DUPLICATE_NAME,
    INVALID,
    UNAVAILABLE,
}

data class TrainingPlanEditorSubmitError(
    val kind: TrainingPlanEditorSubmitErrorKind,
)
