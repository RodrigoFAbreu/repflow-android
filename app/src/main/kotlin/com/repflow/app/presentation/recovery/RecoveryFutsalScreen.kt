package com.repflow.app.presentation.recovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScaleRow
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Stateless screen for recording a day's recovery entry and futsal session
 * (see [RecoveryFutsalViewModel]), as `3c` draws it (remediation-1 CP13):
 * CP3's sub-screen bar with `History`; the date row; the six check-in scales,
 * each labelled at both ends; the futsal toggles and, while "Played in last
 * 24h" is on, the minutes / session-RPE steppers and the training load; notes;
 * and one pinned `Save entry` that reads `Saved` until the next edit.
 */
@Composable
fun RecoveryFutsalScreen(
    uiState: RecoveryFutsalUiState,
    onBack: () -> Unit,
    onHistoryClick: () -> Unit,
    onDateChanged: (LocalDate) -> Unit,
    onScaleFieldChanged: (RecoveryScaleField, Int) -> Unit,
    onFutsalPreviousToggled: (Boolean) -> Unit,
    onFutsalNextToggled: (Boolean) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDurationChanged: (String) -> Unit,
    onSessionRpeChanged: (String) -> Unit,
    onSaveEntry: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    RecoveryErrorMessages(uiState.errorMessage, snackbarHostState, onMessageShown)

    RepFlowScreenScaffold(
        title = stringResource(R.string.recovery_futsal_title),
        onBack = onBack,
        modifier = modifier,
        actions = { HistoryAction(onHistoryClick) },
        snackbarHost = { SnackbarHost(snackbarHostState) { RecoverySnackbar(it) } },
        bottomBar = {
            RepFlowBottomActionBar(
                primaryText =
                    stringResource(if (uiState.isEntrySaved) R.string.recovery_futsal_saved else R.string.recovery_futsal_save_entry),
                onPrimaryClick = onSaveEntry,
                // Not while a date's values load: Save would store the previous
                // date's fields onto the new date (implementation-review revision 1's O8).
                primaryEnabled = !uiState.isSaving && !uiState.isLoading,
                primaryIcon = if (uiState.isEntrySaved) RepFlowIcons.checkFat else null,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = ContentTopPadding, bottom = ContentBottomPadding),
        ) {
            DateRow(date = uiState.date, today = uiState.today, onDateChanged = onDateChanged)
            Spacer(Modifier.height(DateRowBottomGap))
            recoveryScaleSpecs.forEach { spec ->
                RepFlowScaleRow(
                    options = ScaleOptions,
                    selectedIndex = uiState.valueOf(spec.field),
                    onSelect = { onScaleFieldChanged(spec.field, it) },
                    lowLabel = stringResource(spec.lowLabelRes),
                    highLabel = stringResource(spec.highLabelRes),
                    label = stringResource(spec.labelRes),
                    modifier = Modifier.fillMaxWidth().padding(bottom = ScaleRowGap),
                )
            }
            FutsalSection(
                uiState = uiState,
                onFutsalPreviousToggled = onFutsalPreviousToggled,
                onFutsalNextToggled = onFutsalNextToggled,
                onDurationChanged = onDurationChanged,
                onSessionRpeChanged = onSessionRpeChanged,
            )
            NotesField(notes = uiState.notes, onNotesChanged = onNotesChanged)
        }
    }
}

/** `3c`'s trailing `History`: 13.5 in the accent, 44 tall. */
@Composable
private fun HistoryAction(onClick: () -> Unit) {
    val description = stringResource(R.string.recovery_futsal_view_history_content_description)
    Box(
        modifier =
            Modifier
                .heightIn(min = ActionMinHeight)
                .clip(ActionShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = RepFlowSpacing.gapLg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.recovery_futsal_view_history),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = ActionFontSize),
            color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
        )
    }
}

/** Only errors are announced here: a successful save shows on the bar itself (`Saved`). */
@Composable
private fun RecoveryErrorMessages(
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: () -> Unit,
) {
    val invalidText = stringResource(R.string.recovery_futsal_error_invalid)
    val unavailableText = stringResource(R.string.recovery_futsal_error_unavailable)
    LaunchedEffect(errorMessage) {
        if (errorMessage == null) return@LaunchedEffect
        val text = if (errorMessage == RecoveryFutsalViewModel.MESSAGE_INVALID) invalidText else unavailableText
        // Dismiss any still-showing snackbar first (Milestone 8, CP2), so two
        // failures in a row never overlap.
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(text)
        onMessageShown()
    }
}

/** The design's toast card: the surface fill inside a hairline ring, radius 12 (as History's). */
@Composable
private fun RecoverySnackbar(data: SnackbarData) {
    Snackbar(
        modifier =
            Modifier
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd)
                .border(1.dp, RepFlowColor.hairline, SnackbarShape),
        shape = SnackbarShape,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(text = data.visuals.message, style = MaterialTheme.typography.bodyMedium)
    }
}

private val dateRowFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/**
 * `3c`'s date row (`:2028-2032`): `Today · 11 Aug 2026` with `Change`, which
 * opens the date in a sheet (Milestone 8's past-date entry, CP3). Only today
 * or earlier is selectable - an entry records something that already
 * happened.
 */
@Composable
private fun DateRow(
    date: LocalDate,
    today: LocalDate?,
    onDateChanged: (LocalDate) -> Unit,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val formatted = date.format(dateRowFormatter)
    val changeDescription = stringResource(R.string.recovery_futsal_change_date_content_description)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(DateRowShape)
                .background(MaterialTheme.colorScheme.surface, DateRowShape)
                .border(BorderStroke(1.dp, RepFlowColor.hairline), DateRowShape)
                .padding(start = DateRowHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.calendarBlank),
            contentDescription = null,
            tint = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.size(DateIconSize),
        )
        Text(
            text = if (date == today) stringResource(R.string.recovery_futsal_date_today, formatted) else formatted,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = DateFontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = ActionMinHeight)
                    .clickable(role = Role.Button, onClick = { showSheet = true })
                    .semantics { contentDescription = changeDescription }
                    .padding(horizontal = DateRowHorizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.recovery_futsal_change_date),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = ChangeFontSize),
                color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
            )
        }
    }
    if (showSheet) {
        DateSheet(
            date = date,
            latest = today ?: LocalDate.now(),
            onDateChanged = {
                showSheet = false
                onDateChanged(it)
            },
            onDismiss = { showSheet = false },
        )
    }
}

/**
 * Material's date picker in CP3's sheet (`6b`: "sheets for choices"), with
 * `Cancel` and `Set` at 1 : 2 like the keypad. The picker works in UTC
 * midnights, as the old dialog did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSheet(
    date: LocalDate,
    latest: LocalDate,
    onDateChanged: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismiss, title = stringResource(R.string.recovery_futsal_date_sheet_title)) {
        val latestMillis = latest.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis
                    },
            )
        DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.recovery_futsal_date_picker_dismiss),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            RepFlowPrimaryButton(
                text = stringResource(R.string.recovery_futsal_date_picker_confirm),
                onClick = {
                    val selectedMillis = pickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        onDateChanged(Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** `3c`'s `Notes` (`:2081-2082`): a 64-tall multiline field with the design's prompt. */
@Composable
private fun NotesField(
    notes: String,
    onNotesChanged: (String) -> Unit,
) {
    RepFlowSectionLabel(
        text = stringResource(R.string.recovery_futsal_notes),
        modifier = Modifier.padding(top = SectionTopGap, bottom = SectionBottomGap),
    )
    OutlinedTextField(
        value = notes,
        onValueChange = onNotesChanged,
        placeholder = { Text(stringResource(R.string.recovery_futsal_notes_placeholder)) },
        minLines = 2,
        shape = DateRowShape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedBorderColor = RepFlowColor.hairline,
            ),
        modifier = Modifier.fillMaxWidth().heightIn(min = NotesMinHeight),
    )
}

/** The cells every 0-5 scale offers. */
private val ScaleOptions = (RecoveryFutsalUiState.SCALE_MIN..RecoveryFutsalUiState.SCALE_MAX).map { it.toString() }

internal val SectionTopGap = 22.dp
internal val SectionBottomGap = 8.dp
private val ContentTopPadding = 4.dp
private val ContentBottomPadding = 14.dp
private val DateRowShape = RoundedCornerShape(10.dp)
private val DateRowHorizontalPadding = 14.dp
private val DateRowBottomGap = 18.dp
private val DateIconSize = 18.dp
private val DateFontSize = 14.5.sp
private val ChangeFontSize = 13.sp
private val ScaleRowGap = 16.dp
private val ActionMinHeight = 44.dp
private val ActionShape = RoundedCornerShape(8.dp)
private val ActionFontSize = 13.5.sp
private val NotesMinHeight = 64.dp
private val SnackbarShape = RoundedCornerShape(12.dp)
