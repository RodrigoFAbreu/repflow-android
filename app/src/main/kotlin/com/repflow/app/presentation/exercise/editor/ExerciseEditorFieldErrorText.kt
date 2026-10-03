package com.repflow.app.presentation.exercise.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseValidationError
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/**
 * Field/submit-error-to-string-resource mapping for [EditorForm], its
 * inline error line and its field colours, split into their own file purely
 * to keep each file under Detekt's per-file function-count threshold.
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

/** `2b`'s inline error line (`:2385`): a `warning-circle` before the words, never colour alone. */
@Composable
internal fun InlineError(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InlineErrorGap),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.warningCircle),
            contentDescription = null,
            modifier = Modifier.size(InlineErrorIconSize),
        )
        Text(text)
    }
}

/** The design's field: the surface fill behind a hairline, the accent ring while focused. */
@Composable
internal fun editorFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        errorContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = RepFlowColor.hairline,
    )

private val InlineErrorGap = 7.dp
private val InlineErrorIconSize = 14.dp
