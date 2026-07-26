package com.repflow.app.presentation.exercise.editor

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseValidationError

/**
 * Field/submit-error-to-string-resource mapping for [EditorForm], split
 * into its own file purely to keep each file under Detekt's per-file
 * function-count threshold.
 */
@Composable
internal fun SubmitErrorText(submitError: ExerciseEditorSubmitError?) {
    if (submitError == null) return
    val textRes =
        when (submitError.kind) {
            ExerciseEditorSubmitErrorKind.DUPLICATE_NAME -> R.string.exercise_editor_submit_error_duplicate_name
            ExerciseEditorSubmitErrorKind.UNAVAILABLE -> R.string.exercise_editor_submit_error_unavailable
        }
    Text(stringResource(textRes))
}

@Composable
internal fun fieldErrorText(error: ExerciseEditorFieldError?): String? =
    when (error) {
        null -> null
        ExerciseEditorFieldError.InvalidNumber -> stringResource(R.string.exercise_editor_error_invalid_number)
        is ExerciseEditorFieldError.Domain -> domainErrorText(error.error)
    }

@Composable
private fun domainErrorText(error: ExerciseValidationError): String =
    when (error) {
        ExerciseValidationError.NameBlank -> {
            stringResource(R.string.exercise_editor_error_name_blank)
        }

        ExerciseValidationError.NameTooLong -> {
            stringResource(R.string.exercise_editor_error_name_too_long)
        }

        ExerciseValidationError.InstructionsTooLong -> {
            stringResource(R.string.exercise_editor_error_instructions_too_long)
        }

        ExerciseValidationError.LoadIncrementNotSupported -> {
            stringResource(R.string.exercise_editor_error_load_increment_unsupported)
        }

        ExerciseValidationError.LoadIncrementOutOfRange -> {
            stringResource(R.string.exercise_editor_error_load_increment_out_of_range)
        }

        ExerciseValidationError.RestDurationOutOfRange -> {
            stringResource(R.string.exercise_editor_error_rest_duration_out_of_range)
        }

        ExerciseValidationError.UpdatedBeforeCreated, ExerciseValidationError.ArchivedBeforeCreated -> {
            stringResource(R.string.exercise_editor_submit_error_unavailable)
        }
    }
