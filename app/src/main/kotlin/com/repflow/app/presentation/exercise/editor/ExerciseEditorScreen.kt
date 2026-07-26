package com.repflow.app.presentation.exercise.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType

/**
 * Stateless exercise editor screen: state in, events out, no Hilt (see
 * plan.md section H) - Compose-tested directly via `createComposeRule`.
 * Form field composables live in `ExerciseEditorFormFields.kt`, split out
 * purely to keep each file under Detekt's per-file function-count
 * threshold.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    uiState: ExerciseEditorUiState,
    onNameChanged: (String) -> Unit,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onRestSecondsChanged: (String) -> Unit,
    onLoadIncrementChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onBackRequested: () -> Unit,
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
    onMessageShown: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackRequested)
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.messages.firstOrNull()
    val loadIncrementClearedText = stringResource(R.string.exercise_editor_load_increment_cleared)
    LaunchedEffect(message?.id) {
        if (message != null) {
            val text =
                when (message.kind) {
                    ExerciseEditorMessage.Kind.LOAD_INCREMENT_CLEARED -> loadIncrementClearedText
                }
            snackbarHostState.showSnackbar(text)
            onMessageShown(message.id)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(editorTitle(uiState.mode)) },
                navigationIcon = { BackNavigationIcon(onBackRequested) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { innerPadding ->
        EditorBody(
            uiState,
            innerPadding,
            onNameChanged,
            onTrackingTypeChanged,
            onInstructionsChanged,
            onRestSecondsChanged,
            onLoadIncrementChanged,
            onSaveClicked,
        )
    }

    if (uiState.isDiscardDialogVisible) {
        DiscardDialog(onDiscardConfirmed, onDiscardCancelled)
    }
}

@Composable
private fun editorTitle(mode: ExerciseEditorMode): String =
    when (mode) {
        is ExerciseEditorMode.Create -> stringResource(R.string.exercise_editor_title_create)
        is ExerciseEditorMode.Edit -> stringResource(R.string.exercise_editor_title_edit)
    }

@Composable
private fun BackNavigationIcon(onBackRequested: () -> Unit) {
    val contentDescription = stringResource(R.string.exercise_editor_back_content_description)
    IconButton(
        onClick = onBackRequested,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    ) {
        Text("<")
    }
}

@Suppress("LongParameterList")
@Composable
private fun EditorBody(
    uiState: ExerciseEditorUiState,
    innerPadding: PaddingValues,
    onNameChanged: (String) -> Unit,
    onTrackingTypeChanged: (ExerciseTrackingType) -> Unit,
    onInstructionsChanged: (String) -> Unit,
    onRestSecondsChanged: (String) -> Unit,
    onLoadIncrementChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
) {
    when (uiState.loadStatus) {
        ExerciseEditorLoadStatus.LOADING -> {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        ExerciseEditorLoadStatus.NOT_FOUND -> {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.exercise_editor_not_found)) }
        }

        ExerciseEditorLoadStatus.READY -> {
            EditorForm(
                uiState = uiState,
                modifier = Modifier.padding(innerPadding),
                onNameChanged = onNameChanged,
                onTrackingTypeChanged = onTrackingTypeChanged,
                onInstructionsChanged = onInstructionsChanged,
                onRestSecondsChanged = onRestSecondsChanged,
                onLoadIncrementChanged = onLoadIncrementChanged,
                onSaveClicked = onSaveClicked,
            )
        }
    }
}

@Composable
private fun DiscardDialog(
    onDiscardConfirmed: () -> Unit,
    onDiscardCancelled: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDiscardCancelled,
        title = { Text(stringResource(R.string.exercise_editor_discard_dialog_title)) },
        text = { Text(stringResource(R.string.exercise_editor_discard_dialog_body)) },
        confirmButton = {
            Button(onClick = onDiscardConfirmed) { Text(stringResource(R.string.exercise_editor_discard_dialog_confirm)) }
        },
        dismissButton = {
            Button(onClick = onDiscardCancelled) { Text(stringResource(R.string.exercise_editor_discard_dialog_cancel)) }
        },
    )
}
