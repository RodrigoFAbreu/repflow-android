package com.repflow.app.presentation.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/**
 * `Leave this workout?` (`4a` `nExitSheet`, `RepFlow.dc.html:1437-1447`),
 * opened by the board's `X` and by system back. "Leaving is not finishing":
 * the primary action only navigates Home - no use case runs, so the session
 * stays active in Room and its clock keeps running from its stored start.
 *
 * `Finish and save it now` raises the finish sheet ([onFinishNow],
 * remediation-1 CP9) - the one finish surface every finish path goes
 * through - so it completes nothing until that sheet is confirmed. The design's
 * `Discard everything logged` is `Abandon this workout`, behind a destructive
 * confirmation, with today's `AbandonWorkoutSession` behaviour exactly - the
 * session is marked abandoned and nothing is deleted (`D17`, `D18`).
 */
@Composable
internal fun LeaveWorkoutSheet(
    onDismissRequest: () -> Unit,
    onLeaveRunning: () -> Unit,
    onFinishNow: () -> Unit,
    onAbandon: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding)) {
            Text(
                text = stringResource(R.string.workout_leave_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = SheetTitleFontSize, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.workout_leave_body),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            RepFlowAccentOutlineButton(
                text = stringResource(R.string.workout_leave_go_home),
                onClick = onLeaveRunning,
                leadingIcon = RepFlowIcons.house,
                modifier = Modifier.fillMaxWidth().heightIn(min = SheetActionMinHeight),
            )
            Spacer(modifier = Modifier.height(RepFlowSpacing.gapSm))
            RepFlowNeutralOutlineButton(
                text = stringResource(R.string.workout_leave_finish_now),
                onClick = onFinishNow,
                modifier = Modifier.fillMaxWidth().heightIn(min = SheetActionMinHeight),
            )
            Spacer(modifier = Modifier.height(RepFlowSpacing.gapSm))
            TextButton(
                onClick = onAbandon,
                modifier = Modifier.fillMaxWidth().heightIn(min = SheetActionMinHeight),
                shape = SheetActionShape,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.trash),
                    contentDescription = null,
                    modifier = Modifier.size(SheetActionIconSize),
                )
                Text(
                    text = stringResource(R.string.workout_leave_abandon),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = SheetActionFontSize),
                    modifier = Modifier.padding(start = RepFlowSpacing.gapSm),
                )
            }
            Spacer(modifier = Modifier.height(RepFlowSpacing.gapXs))
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth().heightIn(min = KeepTrainingMinHeight),
                shape = SheetActionShape,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    ),
            ) {
                Text(
                    text = stringResource(R.string.workout_leave_keep_training),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/**
 * The exercise picker sheet (`4a` `nPickSheet`, `:1705-1734`), converted from
 * the workout screen's `DropdownMenu`: a search field, `Create a new
 * exercise`, then one row per active exercise. A row adds its exercise to the
 * workout. Each row still carries the recommendation summary and its `Why ›`
 * into the recommendation screen, exactly as the menu row did (plan CP7
 * item 6) - one way in; focus mode's suggestion strip is the other (CP8).
 *
 * `Create a new exercise` opens the existing exercise editor rather than the
 * prototype's inline name-and-type sheet, and the new exercise is then picked
 * here like any other (`D56`). Search matches names only (`D8`).
 */
@Composable
internal fun ExercisePickerSheet(
    availableExercises: List<ExercisePickerItem>,
    onDismissRequest: () -> Unit,
    onAddExercise: (ExercisePickerItem) -> Unit,
    onCreateExercise: () -> Unit,
    onOpenRecommendation: (ExerciseId) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(availableExercises, query) { filterPickerItems(availableExercises, query) }
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RepFlowSpacing.screenPadding)
                        .heightIn(min = SearchFieldMinHeight),
                placeholder = { Text(stringResource(R.string.workout_picker_search_hint)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(RepFlowIcons.magnifyingGlass),
                        contentDescription = null,
                        modifier = Modifier.size(SearchIconSize),
                    )
                },
                trailingIcon =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    painter = painterResource(RepFlowIcons.xCircle),
                                    contentDescription = stringResource(R.string.workout_picker_clear_search),
                                    modifier = Modifier.size(SearchIconSize),
                                )
                            }
                        }
                    } else {
                        null
                    },
                singleLine = true,
                shape = SearchFieldShape,
            )
            RepFlowAccentOutlineButton(
                text = stringResource(R.string.workout_picker_create),
                onClick = onCreateExercise,
                leadingIcon = RepFlowIcons.plusCircle,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapMd),
            )
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (matches.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text =
                                stringResource(
                                    if (availableExercises.isEmpty()) {
                                        R.string.workout_picker_empty_library
                                    } else {
                                        R.string.workout_picker_no_match
                                    },
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
                        )
                    }
                }
                items(items = matches, key = { it.id.value }) { exercise ->
                    PickerRow(
                        exercise = exercise,
                        onClick = { onAddExercise(exercise) },
                        onWhyClick = { onOpenRecommendation(exercise.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    exercise: ExercisePickerItem,
    onClick: () -> Unit,
    onWhyClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = PickerRowMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(exercise.trackingType.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    modifier = Modifier.padding(top = 2.dp),
                )
                exercise.recommendation?.let { recommendation ->
                    RecommendationRow(
                        exerciseName = exercise.name,
                        recommendation = recommendation,
                        onWhyClick = onWhyClick,
                    )
                }
            }
            Icon(
                painter = painterResource(RepFlowIcons.plus),
                contentDescription = null,
                tint = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
                modifier = Modifier.size(PickerPlusIconSize),
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = PICKER_DIVIDER_ALPHA),
        )
    }
}

private fun ExerciseTrackingType.labelRes(): Int =
    when (this) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> R.string.exercise_tracking_type_weight_and_reps
        ExerciseTrackingType.REPS_ONLY -> R.string.exercise_tracking_type_reps_only
        ExerciseTrackingType.DURATION -> R.string.exercise_tracking_type_duration
    }

/** The picker's row divider, `rgba(233,233,237,.07)` - a step under the list rows' 9%. */
private const val PICKER_DIVIDER_ALPHA = 0.07f

private val SheetTitleFontSize = 19.sp
private val SheetActionMinHeight = 52.dp
private val KeepTrainingMinHeight = 48.dp
private val SheetActionShape = RoundedCornerShape(10.dp)
private val SheetActionIconSize = 18.dp
private val SheetActionFontSize = 15.sp

private val SearchFieldMinHeight = 48.dp
private val SearchFieldShape = RoundedCornerShape(10.dp)
private val SearchIconSize = 18.dp
private val PickerRowMinHeight = 60.dp
private val PickerPlusIconSize = 20.dp
