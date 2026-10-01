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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowButtonDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.elapsedLabel
import com.repflow.app.presentation.progression.ProgressionRecommendationUi
import java.math.BigDecimal

/**
 * Focus mode (remediation-1 CP8, `4a` `nFocus`, `RepFlow.dc.html:1240-1362`):
 * one exercise's set entry, opened from its board row. Top to bottom: `Board`
 * / elapsed / `Finish`; `Exercise N of M`, the name and `X of Y sets done`;
 * technique notes when the exercise has them; the set list (logged sets, then
 * the planned sets still to log); `Last:` and `Undo last`; the suggestion
 * strip; the steppers; the RPE / pain / technique disclosure; the type note;
 * the warm-up chip; then the shared rest strip and the pinned bar - `Log set`
 * and `Next ›`.
 *
 * **The planned-target chips are gone.** Warm-up and working progress are
 * the header's `X of Y sets done`; the rep or duration range and the planned
 * rest ride on each not-yet-logged row (`D65`), which wraps rather than
 * clipping, so a target too long for the screen still shows every value.
 */
@Suppress("LongParameterList")
@Composable
internal fun WorkoutFocus(
    content: ActiveWorkoutContent.Active,
    exercise: ActiveExerciseUi,
    recommendation: ProgressionRecommendationUi?,
    onBackToBoard: () -> Unit,
    onFinishClick: () -> Unit,
    onNextExercise: () -> Unit,
    onOpenRecommendation: (ExerciseId) -> Unit,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    restStrip: @Composable () -> Unit,
) {
    val entry = rememberSaveable(exercise.id.value, saver = SetEntryState.Saver) { SetEntryState() }
    var detailExpanded by rememberSaveable(exercise.id.value) { mutableStateOf(false) }
    var correcting by rememberSaveable(exercise.id.value) { mutableStateOf(false) }
    val header = focusHeader(content.exercises, exercise)

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FocusTopBar(startedAt = content.startedAt, onBackToBoard = onBackToBoard, onFinishClick = onFinishClick)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = RepFlowSpacing.screenPadding,
                        end = RepFlowSpacing.screenPadding,
                        top = 4.dp,
                        bottom = RepFlowSpacing.gapLg,
                    ),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            FocusHeader(name = exercise.name, header = header)
            exercise.instructions?.let { TechniqueNotes(exerciseKey = exercise.id.value, instructions = it) }
            FocusSetList(exercise = exercise, onCorrectLast = { correcting = true })
            LastSetLine(exercise = exercise, onUndo = { onUndoLastSet(exercise.id) })
            val exerciseId = exercise.exerciseId
            if (recommendation != null && exerciseId != null) {
                SuggestionStrip(
                    exerciseName = exercise.name,
                    recommendation = recommendation,
                    onWhyClick = { onOpenRecommendation(exerciseId) },
                )
            }
            EntrySteppers(
                trackingType = exercise.trackingType,
                load = entry.load,
                onLoadChange = { entry.load = it },
                reps = entry.reps,
                onRepsChange = { entry.reps = it },
                seconds = entry.seconds,
                onSecondsChange = { entry.seconds = it },
                loadStep = exercise.loadStep(),
                repRange = exercise.plannedTarget?.repRange,
                durationRange = exercise.plannedTarget?.durationRangeSeconds,
            )
            SetDetailSection(entry = entry, expanded = detailExpanded, onExpandedChange = { detailExpanded = it })
            TypeNote(exercise.trackingType)
            WarmupChipRow(
                isWarmup = entry.isWarmup,
                onWarmupChange = { entry.isWarmup = it },
                restSeconds = exercise.restSecondsAfterSet(),
            )
        }
        restStrip()
        FocusBottomBar(
            isWarmup = entry.isWarmup,
            onLogSet = {
                onRecordSet(
                    exercise.id,
                    entry.load?.toDouble(),
                    entry.reps?.toInt(),
                    entry.seconds?.toInt(),
                    entry.rpe?.toDouble(),
                    entry.isWarmup,
                    entry.pain,
                    entry.technique,
                )
                // Cleared immediately (Milestone 8, implementation-review finding #3):
                // each set starts fresh rather than risking an accidental duplicate
                // submit (`D60`).
                entry.clear()
            },
            onNext = onNextExercise,
        )
    }

    val lastSet = exercise.sets.lastOrNull()
    if (correcting && lastSet != null) {
        SetCorrectionSheet(
            exercise = exercise,
            set = lastSet,
            onDismissRequest = { correcting = false },
            onSave = { load, reps, seconds ->
                correcting = false
                onEditLastSet(
                    exercise.id,
                    load?.toDouble(),
                    reps?.toInt(),
                    seconds?.toInt(),
                    lastSet.rpe,
                    lastSet.isWarmup,
                    lastSet.pain,
                    lastSet.techniqueQuality,
                )
            },
        )
    }
}

/**
 * `Board` (`ph-list-bullets`) back to the board, the elapsed clock re-derived
 * from the session's start, and `Finish` as accent text - which raises the
 * finish sheet (remediation-1 CP9), never completing the session itself.
 */
@Composable
private fun FocusTopBar(
    startedAt: java.time.Instant,
    onBackToBoard: () -> Unit,
    onFinishClick: () -> Unit,
) {
    val elapsed = elapsedLabel(rememberElapsedSeconds(startedAt))
    val elapsedDescription = stringResource(R.string.workout_board_elapsed_content_description, elapsed)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = ControlRowMinHeight)
                    .clip(TopBarButtonShape)
                    .clickable(role = Role.Button, onClick = onBackToBoard)
                    .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.listBullets),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_LABEL_ALPHA),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.workout_focus_board),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = TopBarFontSize),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = TOP_BAR_LABEL_ALPHA),
            )
        }
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = elapsedDescription },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.timer),
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = elapsed,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                color = secondary,
            )
        }
        TextButton(onClick = onFinishClick, modifier = Modifier.heightIn(min = ControlRowMinHeight)) {
            Text(
                text = stringResource(R.string.workout_board_finish),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = TopBarFontSize),
                color = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label,
            )
        }
    }
}

/** `Exercise N of M`, the name at 26/500, and `X of Y sets done` (with the planned warm-ups). */
@Composable
private fun FocusHeader(
    name: String,
    header: FocusHeaderUi,
) {
    val workingTarget = header.workingSetTarget
    val sets =
        if (workingTarget != null) {
            pluralStringResource(R.plurals.workout_focus_sets_done, workingTarget, header.workingSetsDone, workingTarget)
        } else {
            pluralStringResource(R.plurals.workout_focus_sets_logged, header.workingSetsDone, header.workingSetsDone)
        }
    val warmups =
        header.warmupSetTarget?.let { target ->
            pluralStringResource(R.plurals.workout_focus_warmups_done, target, header.warmupSetsDone, target)
        }
    Column {
        RepFlowSectionLabel(text = stringResource(R.string.workout_focus_position, header.position, header.exerciseCount))
        Text(
            text = name,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = NameFontSize,
                    lineHeight = NameLineHeight,
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).semantics { heading() },
        )
        Text(
            text = listOfNotNull(sets, warmups).joinToString(separator = " · "),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
    }
}

/**
 * The exercise's technique notes (`docs/UX_FLOWS.md`'s "technique notes" row;
 * `2b`'s "shown during the workout"), as their own collapsed row under the
 * header - read-only, and outside the RPE disclosure on purpose.
 */
@Composable
private fun TechniqueNotes(
    exerciseKey: String,
    instructions: String,
) {
    var open by rememberSaveable(exerciseKey) { mutableStateOf(false) }
    val stateLabel =
        stringResource(if (open) R.string.workout_active_set_detail_expanded else R.string.workout_active_set_detail_collapsed)
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlRowMinHeight)
                    .clip(TopBarButtonShape)
                    .clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = stateLabel },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.info),
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.workout_focus_technique_notes),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(if (open) RepFlowIcons.caretUp else RepFlowIcons.caretDown),
                contentDescription = null,
                tint = secondary,
                modifier = Modifier.size(14.dp),
            )
        }
        if (open) {
            Text(
                text = instructions,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** `Body-weight exercise — reps only.` / `Timed hold — no load recorded for this one.` (`:1339-1341`). */
@Composable
private fun TypeNote(trackingType: ExerciseTrackingType) {
    val text =
        when (trackingType) {
            ExerciseTrackingType.WEIGHT_AND_REPS -> return
            ExerciseTrackingType.REPS_ONLY -> stringResource(R.string.workout_focus_type_note_reps)
            ExerciseTrackingType.DURATION -> stringResource(R.string.workout_focus_type_note_duration)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.info),
            contentDescription = null,
            tint = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
    }
}

/** `Last: 82.5 kg × 8` and `Undo last` (`:1272-1276`); nothing until a set is logged. */
@Composable
private fun LastSetLine(
    exercise: ActiveExerciseUi,
    onUndo: () -> Unit,
) {
    val last = exercise.sets.lastOrNull() ?: return
    val load = last.load
    val body =
        when {
            exercise.trackingType == ExerciseTrackingType.DURATION -> {
                stringResource(R.string.workout_focus_last_duration, last.durationSeconds ?: 0)
            }

            exercise.trackingType == ExerciseTrackingType.WEIGHT_AND_REPS && load != null -> {
                stringResource(
                    R.string.workout_focus_last_weight_reps,
                    RepFlowStepperMath.format(BigDecimal.valueOf(load)),
                    last.reps ?: 0,
                )
            }

            else -> {
                pluralStringResource(R.plurals.workout_focus_last_reps, last.reps ?: 0, last.reps ?: 0)
            }
        }
    val text = if (last.isWarmup) body + " " + stringResource(R.string.workout_active_warmup_suffix) else body
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = secondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onUndo,
            modifier = Modifier.heightIn(min = ControlRowMinHeight),
            contentPadding = PaddingValues(horizontal = 10.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = secondary),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.arrowCounterClockwise),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.workout_focus_undo_last),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                modifier = Modifier.padding(start = RepFlowSpacing.gapXs),
            )
        }
    }
}

/**
 * The suggestion strip (`:1278-1284`): the exercise's progression
 * recommendation - the proposed action and the policy's top reason - and
 * `Why ›` into the recommendation screen (`D27`; plan CP8 item 5). Absent when
 * the exercise has no recommendation.
 */
@Composable
private fun SuggestionStrip(
    exerciseName: String,
    recommendation: ProgressionRecommendationUi,
    onWhyClick: () -> Unit,
) {
    val whyDescription = stringResource(R.string.progression_why_content_description, exerciseName)
    val accent = repFlowAccentOutlineColors(MaterialTheme.colorScheme).label
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, RepFlowColor.hairline), StripShape)
                .padding(start = RepFlowSpacing.gapLg, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.pulse),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = recommendationSummary(recommendation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onWhyClick,
            modifier =
                Modifier
                    .heightIn(min = ControlRowMinHeight)
                    .semantics { contentDescription = whyDescription },
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(stringResource(R.string.progression_why_link), style = MaterialTheme.typography.labelLarge, color = accent)
            Icon(
                painter = painterResource(RepFlowIcons.caretRight),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(start = 4.dp).size(12.dp),
            )
        }
    }
}

/**
 * The pinned bar (`:1357-1360`): `Log set` - `Log warm-up` while the warm-up
 * chip is on - as the one primary, and `Next ›` beside it. On the last
 * unfinished exercise `Next ›` returns to the board with the finish sheet
 * raised, as the prototype's `nNextExercise` does (remediation-1 CP9).
 */
@Composable
private fun FocusBottomBar(
    isWarmup: Boolean,
    onLogSet: () -> Unit,
    onNext: () -> Unit,
) {
    RepFlowBottomActionBar {
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
            RepFlowPrimaryButton(
                text = stringResource(if (isWarmup) R.string.workout_focus_log_warmup else R.string.workout_focus_log_set),
                onClick = onLogSet,
                leadingIcon = RepFlowIcons.checkFat,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.heightIn(min = RepFlowButtonDefaults.primaryMinHeight),
                shape = RepFlowButtonDefaults.primaryShape,
                border = BorderStroke(RepFlowButtonDefaults.borderWidth, RepFlowColor.hairline),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowButtonDefaults.neutralOutlineLabelAlpha),
                    ),
            ) {
                Text(
                    text = stringResource(R.string.workout_focus_next),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    textAlign = TextAlign.Center,
                )
                Icon(
                    painter = painterResource(RepFlowIcons.caretRight),
                    contentDescription = null,
                    modifier = Modifier.padding(start = RepFlowSpacing.gapXs).size(14.dp),
                )
            }
        }
    }
}

private const val TOP_BAR_LABEL_ALPHA = 0.8f

private val TopBarButtonShape = RoundedCornerShape(8.dp)
private val TopBarFontSize = 13.5.sp

/** The name at 26/500, line-height 1.16. */
private val NameFontSize = 26.sp
private val NameLineHeight = 30.sp

private val StripShape = RoundedCornerShape(12.dp)
