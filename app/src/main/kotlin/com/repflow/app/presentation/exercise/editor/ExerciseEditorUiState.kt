package com.repflow.app.presentation.exercise.editor

import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.exercise.ExerciseValidationError

/**
 * Unidirectional editor state (see plan.md section H).
 *
 * Text fields are kept as raw strings, never as domain value objects, so
 * they round-trip through [androidx.lifecycle.SavedStateHandle] as
 * primitives (D-22). [nameError] and the other field errors hold the
 * *domain* validation failure for the field's current raw value, recomputed
 * on every change so [isSaveEnabled] can reflect the current draft without
 * a separate "has been validated" flag.
 */
data class ExerciseEditorUiState(
    val mode: ExerciseEditorMode = ExerciseEditorMode.Create,
    val loadStatus: ExerciseEditorLoadStatus = ExerciseEditorLoadStatus.READY,
    val name: String = "",
    val trackingType: ExerciseTrackingType = ExerciseTrackingType.WEIGHT_AND_REPS,
    val instructions: String = "",
    val restSecondsText: String = "",
    val loadIncrementKgText: String = "",
    val nameError: ExerciseEditorFieldError? = null,
    /** Set once the user has typed in the name field (or it arrived non-empty); a pristine form shows no error. */
    val nameTouched: Boolean = false,
    val instructionsError: ExerciseEditorFieldError? = null,
    val restDurationError: ExerciseEditorFieldError? = null,
    val loadIncrementError: ExerciseEditorFieldError? = null,
    val isSaving: Boolean = false,
    val isDiscardDialogVisible: Boolean = false,
    val savedExerciseId: ExerciseId? = null,
    val dismissed: Boolean = false,
    val submitError: ExerciseEditorSubmitError? = null,
    val messages: List<ExerciseEditorMessage> = emptyList(),
) {
    /** The name's validation error, shown only once the field has been touched. */
    val visibleNameError: ExerciseEditorFieldError?
        get() = nameError.takeIf { nameTouched }

    /**
     * `false` while loading (edit mode), while a required field is invalid,
     * while [name] is blank, or while a save is already in flight - the last
     * of which prevents concurrent/double submission (implementation
     * correction 10).
     */
    val isSaveEnabled: Boolean
        get() =
            !isSaving &&
                loadStatus == ExerciseEditorLoadStatus.READY &&
                name.isNotBlank() &&
                nameError == null &&
                instructionsError == null &&
                restDurationError == null &&
                loadIncrementError == null
}

/** Only meaningful in [ExerciseEditorMode.Edit]; [ExerciseEditorMode.Create] starts at [READY]. */
enum class ExerciseEditorLoadStatus {
    LOADING,
    READY,
    NOT_FOUND,
}

/**
 * A field's current problem, distinguishing an unparsable raw value
 * ([InvalidNumber], a presentation-only concern) from a domain invariant
 * violation ([Domain], forwarded from [ExerciseValidationError]).
 */
sealed interface ExerciseEditorFieldError {
    data class Domain(
        val error: ExerciseValidationError,
    ) : ExerciseEditorFieldError

    data object InvalidNumber : ExerciseEditorFieldError
}

enum class ExerciseEditorSubmitErrorKind {
    DUPLICATE_NAME,
    UNAVAILABLE,
}

data class ExerciseEditorSubmitError(
    val kind: ExerciseEditorSubmitErrorKind,
)

/**
 * A one-off editor notification with a stable, unique [id], consumed
 * exclusively via `onMessageShown(id)` (implementation correction 11) so a
 * newer message queued while an older one is still displayed is never
 * dropped.
 */
data class ExerciseEditorMessage(
    val id: Long,
    val kind: Kind,
) {
    enum class Kind {
        LOAD_INCREMENT_CLEARED,
    }
}
