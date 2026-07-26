package com.repflow.app.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Stateless screen for recording today's recovery entry and futsal session
 * (see [RecoveryFutsalViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryFutsalScreen(
    uiState: RecoveryFutsalUiState,
    onHistoryClick: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onScaleFieldChanged: (RecoveryScaleField, Int) -> Unit,
    onFutsalPreviousToggled: (Boolean) -> Unit,
    onFutsalNextToggled: (Boolean) -> Unit,
    onNotesChanged: (String) -> Unit,
    onSaveRecovery: () -> Unit,
    onDurationChanged: (String) -> Unit,
    onSessionRpeChanged: (String) -> Unit,
    onSaveFutsal: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val savedText = stringResource(R.string.recovery_futsal_saved)
    val invalidText = stringResource(R.string.recovery_futsal_error_invalid)
    val unavailableText = stringResource(R.string.recovery_futsal_error_unavailable)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.recoverySavedMessage, uiState.futsalSavedMessage, uiState.errorMessage) {
        val text =
            when {
                uiState.errorMessage == "invalid" -> invalidText
                uiState.errorMessage != null -> unavailableText
                uiState.recoverySavedMessage != null || uiState.futsalSavedMessage != null -> savedText
                else -> null
            }
        if (text != null) {
            // Dismiss any still-showing snackbar first (Milestone 8, CP2) - guards
            // against overlapping feedback if a save resolves while an earlier one's
            // snackbar is still up, rather than relying only on implicit
            // LaunchedEffect-restart cancellation timing.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recovery_futsal_title)) },
                actions = {
                    val historyContentDescription = stringResource(R.string.recovery_futsal_view_history_content_description)
                    TextButton(
                        onClick = onHistoryClick,
                        modifier = Modifier.semantics { contentDescription = historyContentDescription },
                    ) {
                        Text(stringResource(R.string.recovery_futsal_view_history))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DateRow(date = uiState.date, onDateChanged = onDateChanged)

            Text(stringResource(R.string.recovery_futsal_recovery_section_title))
            ScaleStepperRow(
                R.string.recovery_futsal_sleep_quality,
                uiState.sleepQuality,
            ) { onScaleFieldChanged(RecoveryScaleField.SLEEP_QUALITY, it) }
            ScaleStepperRow(R.string.recovery_futsal_energy, uiState.energy) { onScaleFieldChanged(RecoveryScaleField.ENERGY, it) }
            ScaleStepperRow(
                R.string.recovery_futsal_leg_doms,
                uiState.legDoms,
            ) { onScaleFieldChanged(RecoveryScaleField.LEG_DOMS, it) }
            ScaleStepperRow(
                R.string.recovery_futsal_heel_stiffness,
                uiState.heelStiffness,
            ) { onScaleFieldChanged(RecoveryScaleField.HEEL_STIFFNESS, it) }
            ScaleStepperRow(
                R.string.recovery_futsal_pain_while_walking,
                uiState.painWhileWalking,
            ) { onScaleFieldChanged(RecoveryScaleField.PAIN_WHILE_WALKING, it) }
            ScaleStepperRow(
                R.string.recovery_futsal_heavy_legs,
                uiState.heavyLegs,
            ) { onScaleFieldChanged(RecoveryScaleField.HEAVY_LEGS, it) }
            ToggleRow(R.string.recovery_futsal_futsal_previous_24h, uiState.futsalInPrevious24h, onFutsalPreviousToggled)
            ToggleRow(R.string.recovery_futsal_futsal_next_24h, uiState.futsalExpectedNext24h, onFutsalNextToggled)
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = onNotesChanged,
                label = { Text(stringResource(R.string.recovery_futsal_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSaveRecovery, enabled = !uiState.isSavingRecovery) {
                Text(stringResource(R.string.recovery_futsal_save_recovery))
            }

            HorizontalDivider()

            Text(stringResource(R.string.recovery_futsal_futsal_section_title))
            OutlinedTextField(
                value = uiState.durationMinutesInput,
                onValueChange = onDurationChanged,
                label = { Text(stringResource(R.string.recovery_futsal_duration_minutes)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.sessionRpeInput,
                onValueChange = onSessionRpeChanged,
                label = { Text(stringResource(R.string.recovery_futsal_session_rpe)) },
                keyboardOptions =
                    androidx.compose.foundation.text
                        .KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            uiState.futsalLoad?.let { load ->
                Text(stringResource(R.string.recovery_futsal_load, load.toString()))
            }
            TextButton(onClick = onSaveFutsal, enabled = !uiState.isSavingFutsal) {
                Text(stringResource(R.string.recovery_futsal_save_futsal))
            }
        }
    }
}

private val dateRowFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * Shows the entry's [date] and a button to change it (Milestone 8, CP3).
 * Only today or earlier is selectable - a recovery/futsal entry records
 * something that already happened, so a future date would never be a real
 * value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    date: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(date.format(dateRowFormatter), modifier = Modifier.weight(1f))
        TextButton(onClick = { showPicker = true }) {
            Text(stringResource(R.string.recovery_futsal_change_date))
        }
    }
    if (showPicker) {
        val initialMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = initialMillis,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= System.currentTimeMillis()
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = pickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val selectedDate =
                            Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChanged(selectedDate)
                    }
                    showPicker = false
                }) {
                    Text(stringResource(R.string.recovery_futsal_date_picker_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.recovery_futsal_date_picker_dismiss))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun ScaleStepperRow(
    labelRes: Int,
    value: Int,
    onValueChanged: (Int) -> Unit,
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        TextButton(onClick = { onValueChanged(value - 1) }) { Text("-") }
        Text(value.toString())
        TextButton(onClick = { onValueChanged(value + 1) }) { Text("+") }
    }
}

@Composable
private fun ToggleRow(
    labelRes: Int,
    value: Boolean,
    onValueChanged: (Boolean) -> Unit,
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValueChanged)
    }
}
