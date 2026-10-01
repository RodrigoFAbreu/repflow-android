package com.repflow.app.presentation.trainingplan.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * `4a`'s plan picker sheet (`nPlanPickOpen`, `RepFlow.dc.html:1635-1659`) in
 * place of the row's old `DropdownMenu` (plan CP11 item 2): `Add to plan`, a
 * 48 search over the names, `Create a new exercise`, then 60-tall rows - the
 * name over its tracking type - each ending in an accent `plus`. Choosing a row
 * hands its id back; the screen decides which plan row it fills.
 *
 * The options are the editor's own `availableExercises` (active exercises, kept
 * live by the ViewModel), so an exercise created from here is in the list the
 * next time the sheet opens. `2a`'s muscle-group chips are not drawn (`D8`).
 */
@Composable
internal fun ExercisePickerSheet(
    options: List<TrainingPlanEditorExerciseOption>,
    onSelect: (String) -> Unit,
    onCreateExerciseClick: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = options.filter { it.name.contains(query.trim(), ignoreCase = true) }
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        Text(
            text = stringResource(R.string.training_plan_editor_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = RepFlowSpacing.screenPadding).padding(bottom = RepFlowSpacing.gapMd),
        )
        PickerSearchField(query = query, onQueryChanged = { query = it })
        RepFlowAccentOutlineButton(
            text = stringResource(R.string.training_plan_editor_picker_create),
            onClick = onCreateExerciseClick,
            leadingIcon = RepFlowIcons.plusCircle,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = RepFlowSpacing.screenPadding)
                    .padding(bottom = RepFlowSpacing.gapMd),
        )
        if (matches.isEmpty()) {
            Text(
                text = stringResource(R.string.training_plan_editor_picker_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.padding(RepFlowSpacing.screenPadding),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = matches, key = { it.id }) { option ->
                PickerRow(option = option, onClick = { onSelect(option.id) })
            }
        }
    }
}

@Composable
private fun PickerSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        placeholder = { Text(stringResource(R.string.training_plan_editor_picker_search_hint)) },
        singleLine = true,
        shape = SearchShape,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = RepFlowColor.control,
                unfocusedContainerColor = RepFlowColor.control,
                unfocusedBorderColor = RepFlowColor.hairline,
            ),
        leadingIcon = {
            Icon(
                painter = painterResource(RepFlowIcons.magnifyingGlass),
                contentDescription = null,
                tint = repFlowSecondaryTextColor(scheme),
                modifier = Modifier.size(PickerGlyphSize),
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RepFlowSpacing.screenPadding)
                .padding(bottom = RepFlowSpacing.gapMd)
                .heightIn(min = SearchMinHeight),
    )
}

@Composable
private fun PickerRow(
    option: TrainingPlanEditorExerciseOption,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PickerRowMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = RepFlowSpacing.screenPadding, vertical = PickerRowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = trackingTypeLabel(option.trackingType),
                    style = MaterialTheme.typography.bodySmall,
                    color = repFlowSecondaryTextColor(scheme),
                )
            }
            Icon(
                painter = painterResource(RepFlowIcons.plus),
                contentDescription = null,
                tint = repFlowAccentOutlineColors(scheme).label,
                modifier = Modifier.size(PickerGlyphSize),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = scheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha))
    }
}

@Composable
private fun trackingTypeLabel(trackingType: ExerciseTrackingType): String =
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> stringResource(R.string.exercise_tracking_type_weight_and_reps)
        ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.exercise_tracking_type_reps_only)
        ExerciseTrackingType.DURATION -> stringResource(R.string.exercise_tracking_type_duration)
    }

private val SearchShape = RoundedCornerShape(10.dp)
private val SearchMinHeight = 48.dp
private val PickerRowMinHeight = 60.dp
private val PickerRowVerticalPadding = 12.dp
private val PickerGlyphSize = 18.dp
