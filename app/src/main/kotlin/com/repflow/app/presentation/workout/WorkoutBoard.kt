package com.repflow.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.elapsedLabel
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * The workout board (remediation-1 CP7, `4a` `nBoard`,
 * `RepFlow.dc.html:1161-1238`): top bar, progress line, one row per
 * exercise, `Add exercise`, and the rest strip pinned under the list.
 *
 * Out-of-order work is the point of the board - `up next` is a hint, not a
 * lock, and every row opens that exercise's set entry. A row carries no `⋮`
 * and no row sheet: none of the sheet's six options has domain backing
 * (plan CP7 item 7; `D1`-`D3`, `D13`-`D15`), and a trigger that opened an
 * empty sheet would be worse than its absence.
 */

@Composable
internal fun WorkoutBoard(
    content: ActiveWorkoutContent.Active,
    dayContext: WorkoutDayContextUi?,
    onLeaveClick: () -> Unit,
    onFinishClick: () -> Unit,
    onExerciseClick: (WorkoutExerciseId) -> Unit,
    onAddExerciseClick: () -> Unit,
    restStrip: @Composable () -> Unit,
) {
    val rows = remember(content.exercises) { boardRows(content.exercises) }
    val progress = remember(content.exercises) { boardProgress(content.exercises) }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BoardTopBar(
            title = content.planName ?: stringResource(R.string.home_untitled_workout),
            startedAt = content.startedAt,
            onLeaveClick = onLeaveClick,
            onFinishClick = onFinishClick,
        )
        BoardHeader(progress = progress, dayContext = dayContext)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding =
                PaddingValues(
                    start = RepFlowSpacing.screenPadding,
                    end = RepFlowSpacing.screenPadding,
                    bottom = RepFlowSpacing.gapSm,
                ),
        ) {
            if (rows.isEmpty()) {
                item(key = "empty") { EmptyBoard() }
            }
            items(items = rows, key = { it.id.value }) { row ->
                BoardRow(row = row, onClick = { onExerciseClick(row.id) })
            }
            item(key = "add") {
                RepFlowAccentOutlineButton(
                    text = stringResource(R.string.workout_active_add_exercise),
                    onClick = onAddExerciseClick,
                    leadingIcon = RepFlowIcons.plus,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = AddExerciseTopMargin)
                            .heightIn(min = AddExerciseMinHeight),
                )
            }
        }
        restStrip()
    }
}

/**
 * `X` (leave), the title over `ph-timer` and the elapsed clock, `Finish`.
 * The title is not a button: renaming an ad-hoc workout has no domain
 * backing (`D30`). The elapsed time is re-derived from the session's stored
 * start every second - never counted - so it survives leaving and coming
 * back exactly as Home's resume card does. `Finish` raises the finish sheet
 * (remediation-1 CP9) - it never completes the session itself - and sits at
 * the design system's accent-outline tier rather than the prototype's one-off
 * 40 (`D59`).
 */
@Composable
private fun BoardTopBar(
    title: String,
    startedAt: Instant,
    onLeaveClick: () -> Unit,
    onFinishClick: () -> Unit,
) {
    val elapsed = elapsedLabel(rememberElapsedSeconds(startedAt))
    val leaveDescription = stringResource(R.string.workout_board_leave_content_description)
    val elapsedDescription = stringResource(R.string.workout_board_elapsed_content_description, elapsed)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Box(
            modifier =
                Modifier
                    .size(TopBarButtonSize)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onLeaveClick)
                    .semantics { contentDescription = leaveDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.x),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = LEAVE_ICON_ALPHA),
                modifier = Modifier.size(LeaveIconSize),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = TitleFontSize),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = elapsedDescription },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ElapsedGap),
            ) {
                val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
                Icon(
                    painter = painterResource(RepFlowIcons.timer),
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(ElapsedIconSize),
                )
                Text(
                    text = elapsed,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = ElapsedFontSize, fontFeatureSettings = "tnum"),
                    color = secondary,
                )
            }
        }
        RepFlowAccentOutlineButton(
            text = stringResource(R.string.workout_board_finish),
            onClick = onFinishClick,
        )
    }
}

/** `N of M exercises · S/T sets`, then the session's recovery/futsal context when there is any. */
@Composable
private fun BoardHeader(
    progress: BoardProgressUi,
    dayContext: WorkoutDayContextUi?,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val metaStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = RepFlowSpacing.screenPadding, end = RepFlowSpacing.screenPadding, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        Text(text = progressLabel(progress), style = metaStyle, color = secondary)
        dayContextLabel(dayContext)?.let { Text(text = it, style = metaStyle, color = secondary) }
    }
}

/** The progress line - the board's header and the finish sheet's meta line (remediation-1 CP9) read it alike. */
@Composable
internal fun progressLabel(progress: BoardProgressUi): String =
    if (progress.targetSets != null) {
        pluralStringResource(
            R.plurals.workout_board_progress,
            progress.exerciseCount,
            progress.exercisesDone,
            progress.exerciseCount,
            progress.setsDone,
            progress.targetSets,
        )
    } else {
        pluralStringResource(
            R.plurals.workout_board_progress_untargeted,
            progress.setsDone,
            progress.setsDone,
            progress.exercisesDone,
            progress.exerciseCount,
        )
    }

/**
 * The recovery and futsal context the workout screen showed before the
 * board (heavy legs, leg DOMS, futsal in the last day), kept as one meta
 * line. `4a`'s board draws no such line; dropping it would remove a reading
 * the app shows today with nothing in its place (deviation `D57`).
 */
@Composable
private fun dayContextLabel(dayContext: WorkoutDayContextUi?): String? {
    if (dayContext == null) return null
    val parts =
        listOfNotNull(
            dayContext.heavyLegs?.let { stringResource(R.string.workout_day_context_heavy_legs, it) },
            dayContext.legDoms?.let { stringResource(R.string.workout_day_context_leg_doms, it) },
            dayContext.futsalLoad?.let { stringResource(R.string.workout_day_context_futsal_load, it) },
        )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
}

/**
 * One exercise: its name with the `up next` hint, then the status chip - a
 * word and a glyph in every state (`6b`, "never colour alone"). The whole
 * row opens the exercise's set entry; there is nothing else to tap.
 */
@Composable
private fun BoardRow(
    row: BoardRowUi,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BoardRowMinHeight)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 2.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
            ) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = RowNameFontSize),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.isUpNext) {
                    RepFlowStatusChip(
                        text = stringResource(R.string.workout_board_up_next).uppercase(locale),
                        tone = RepFlowTagTone.UpNext,
                    )
                }
            }
            BoardStatusChip(row)
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
        )
    }
}

@Composable
private fun BoardStatusChip(row: BoardRowUi) {
    val target = row.targetWorkingSets
    val text =
        when {
            row.status == BoardRowStatus.NOT_STARTED && target != null -> {
                pluralStringResource(R.plurals.workout_board_status_target, target, target)
            }

            row.status == BoardRowStatus.NOT_STARTED -> {
                stringResource(R.string.workout_board_status_none)
            }

            target != null -> {
                pluralStringResource(R.plurals.workout_board_status_progress, target, target, row.workingSetsDone)
            }

            else -> {
                pluralStringResource(R.plurals.workout_board_status_logged, row.workingSetsDone, row.workingSetsDone)
            }
        }
    val (tone, icon) =
        when (row.status) {
            BoardRowStatus.DONE -> RepFlowTagTone.Done to RepFlowIcons.checkFat
            BoardRowStatus.IN_PROGRESS -> RepFlowTagTone.Pending to RepFlowIcons.dotOutline
            BoardRowStatus.NOT_STARTED -> RepFlowTagTone.Outline to RepFlowIcons.circle
        }
    RepFlowStatusChip(text = text, tone = tone, icon = icon)
}

/** `4a`'s empty board: the clock is already running, add whatever machine is free. */
@Composable
private fun EmptyBoard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = RepFlowSpacing.gapSm)
                .border(BorderStroke(1.dp, RepFlowColor.hairline), EmptyBoardShape)
                .padding(horizontal = 18.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.barbell),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_ICON_ALPHA),
            modifier = Modifier.size(EmptyIconSize),
        )
        Text(
            text = stringResource(R.string.workout_board_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = EmptyTitleFontSize, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        Text(
            text = stringResource(R.string.workout_board_empty_body),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = EmptyBodyFontSize),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            textAlign = TextAlign.Center,
        )
    }
}

/** Seconds since [startedAt], re-read from the wall clock every second - derived, never counted. */
@Composable
internal fun rememberElapsedSeconds(startedAt: Instant): Long {
    var elapsed by remember(startedAt) { mutableLongStateOf(secondsSince(startedAt)) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = secondsSince(startedAt)
            delay(ELAPSED_TICK_MILLIS)
        }
    }
    return elapsed
}

private fun secondsSince(startedAt: Instant): Long = (Instant.now().toEpochMilli() - startedAt.toEpochMilli()) / MILLIS_PER_SECOND

private const val ELAPSED_TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val LEAVE_ICON_ALPHA = 0.75f
private const val EMPTY_ICON_ALPHA = 0.35f

/** `6b`'s 44 tap target (the `X`). */
private val TopBarButtonSize = 44.dp
private val LeaveIconSize = 20.dp
private val TitleFontSize = 16.sp
private val ElapsedFontSize = 12.sp
private val ElapsedIconSize = 12.dp
private val ElapsedGap = 5.dp

/** `min-height:64px`, name 15.5/500. */
private val BoardRowMinHeight = 64.dp
private val RowNameFontSize = 15.5.sp

/** `margin-top:14px; min-height:52px`. */
private val AddExerciseTopMargin = 14.dp
private val AddExerciseMinHeight = 52.dp

private val EmptyBoardShape = RoundedCornerShape(14.dp)
private val EmptyIconSize = 28.dp
private val EmptyTitleFontSize = 18.sp
private val EmptyBodyFontSize = 13.5.sp
