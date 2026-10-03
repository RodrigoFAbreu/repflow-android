package com.repflow.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.elapsedLabel

/**
 * `Finish this workout?` (remediation-1 CP9, `4a` `nFinishSheet`,
 * `RepFlow.dc.html:1736-1763`): the elapsed time and the board's progress
 * line, what is still unfinished, then `Finish and save` / `Keep training` /
 * `Leave it running and go Home`.
 *
 * **This is the one finish surface.** Every finish entry point raises it - the
 * board's and focus mode's `Finish`, the leave sheet's `Finish and save it
 * now`, `Next ›` with nothing unfinished left, and Home's `Finish it` - and its
 * confirm ([onConfirm]) is the only path to `CompleteWorkoutSession`. The
 * unfinished list is [unfinishedExercises], the board's own rule.
 *
 * `Finish and save` sits at the design system's 56dp primary tier rather than
 * the prototype's one-off 52, as the keypad's `Set` does (`D40`).
 */
@Composable
internal fun WorkoutFinishSheet(
    content: ActiveWorkoutContent.Active,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    onKeepTraining: () -> Unit,
    onLeaveRunning: () -> Unit,
) {
    val elapsed = elapsedLabel(rememberElapsedSeconds(content.startedAt))
    val progress = progressLabel(boardProgress(content.exercises))
    val unfinished = unfinishedExercises(content.exercises)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = RepFlowSpacing.screenPadding)) {
            Text(
                text = stringResource(R.string.workout_finish_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = FinishTitleFontSize, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(
                text = stringResource(R.string.workout_finish_meta, elapsed, progress),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = FinishMetaFontSize, fontFeatureSettings = "tnum"),
                color = secondary,
            )
            if (unfinished.isNotEmpty()) {
                UnfinishedBox(unfinished)
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
            ) {
                RepFlowPrimaryButton(
                    text = stringResource(R.string.workout_finish_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.workout_finish_keep_training),
                    onClick = onKeepTraining,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FinishSecondaryMinHeight),
                )
                TextButton(
                    onClick = onLeaveRunning,
                    modifier = Modifier.fillMaxWidth().heightIn(min = FinishLeaveMinHeight),
                    shape = FinishActionShape,
                    colors = ButtonDefaults.textButtonColors(contentColor = secondary),
                ) {
                    Text(text = stringResource(R.string.workout_leave_go_home), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                text = stringResource(R.string.workout_finish_footnote),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = FinishFootnoteFontSize),
                color = secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

/** `N still unfinished`, then one `○ <name> … N sets left` row per exercise, inside a hairline box. */
@Composable
private fun UnfinishedBox(unfinished: List<UnfinishedExerciseUi>) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .border(BorderStroke(1.dp, RepFlowColor.hairline), UnfinishedBoxShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.workout_finish_unfinished_count, unfinished.size, unfinished.size),
            style = MaterialTheme.typography.bodySmall,
            color = secondary,
            modifier = Modifier.padding(bottom = RepFlowSpacing.gapSm),
        )
        unfinished.forEach { exercise ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.circle),
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(UnfinishedIconSize),
                )
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = UnfinishedRowFontSize),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text =
                        exercise.setsLeft?.let { left ->
                            pluralStringResource(R.plurals.workout_finish_sets_left, left, left)
                        } ?: stringResource(R.string.workout_board_status_none),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = UnfinishedRowFontSize, fontFeatureSettings = "tnum"),
                    color = secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

private val FinishTitleFontSize = 20.sp
private val FinishMetaFontSize = 13.5.sp
private val FinishFootnoteFontSize = 12.sp
private val FinishSecondaryMinHeight = 52.dp
private val FinishLeaveMinHeight = 48.dp
private val FinishActionShape = RoundedCornerShape(10.dp)
private val UnfinishedBoxShape = RoundedCornerShape(12.dp)
private val UnfinishedIconSize = 11.dp
private val UnfinishedRowFontSize = 13.5.sp
