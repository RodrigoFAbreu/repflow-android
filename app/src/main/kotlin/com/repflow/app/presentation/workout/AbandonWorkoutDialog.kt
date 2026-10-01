package com.repflow.app.presentation.workout

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.repflow.app.R

/**
 * `Abandon this workout?` - the destructive confirmation in front of the
 * existing `AbandonWorkoutSession` (`6b`: "dialogs only for destructive
 * confirmation"). One dialog for both ways to abandon: Home's resume-card
 * trash (remediation-1 CP5) and the workout leave sheet's `Abandon this
 * workout` (CP7). The copy says what abandoning actually does - the session
 * is marked abandoned and its sets stay stored - rather than the design's
 * `Discard everything logged` (`D17`, `D18`).
 */
@Composable
internal fun AbandonWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_abandon_confirm_title)) },
        text = { Text(stringResource(R.string.workout_abandon_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.workout_abandon_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workout_abandon_keep_action))
            }
        },
    )
}
