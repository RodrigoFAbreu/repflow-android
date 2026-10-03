package com.repflow.app.presentation.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseProgress
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/*
 * `5b`'s exercise picker: a full-width button (min-height 52, radius 10, barbell,
 * the name, a caret) that opens the `Track an exercise` sheet - rows at least 56
 * tall, the chosen one tinted and carrying a check, the list scrolling when it
 * is long. It lists the exercises with counted history, most recent first.
 */

@Composable
internal fun ExercisePickerButton(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ButtonRadius)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ButtonMinHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .border(BorderStroke(1.dp, RepFlowColor.hairline), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = ButtonHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.barbell),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = BARBELL_ALPHA),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(RepFlowIcons.caretDown),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = CARET_ALPHA),
        )
    }
}

@Composable
internal fun ExercisePickerSheet(
    exercises: List<ExerciseProgress>,
    selectedId: ExerciseId,
    onSelect: (ExerciseId) -> Unit,
    onDismiss: () -> Unit,
) {
    // Long lists scroll inside the sheet rather than growing past the screen.
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * LIST_HEIGHT_FRACTION).toDp() }
    RepFlowSheet(onDismissRequest = onDismiss, title = stringResource(R.string.progress_picker_title)) {
        Column(
            modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).selectableGroup(),
        ) {
            exercises.forEach { exercise ->
                PickerRow(
                    name = exercise.name,
                    selected = exercise.exerciseId == selectedId,
                    onClick = { onSelect(exercise.exerciseId) },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = SELECTED_ROW_ALPHA) else Color.Transparent)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ButtonGap),
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                painter = painterResource(RepFlowIcons.checkFat),
                contentDescription = null,
                tint = colors.label,
                modifier = Modifier.size(CheckSize),
            )
        }
    }
}

private const val BARBELL_ALPHA = 0.55f
private const val CARET_ALPHA = 0.45f
private const val SELECTED_ROW_ALPHA = 0.10f
private const val LIST_HEIGHT_FRACTION = 0.6f

private val ButtonRadius = 10.dp
private val ButtonMinHeight = 52.dp
private val ButtonHorizontalPadding = 14.dp
private val ButtonGap = 10.dp
private val RowMinHeight = 56.dp
private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val CheckSize = 13.dp
