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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.domain.workout.WorkoutExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowNeutralOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/**
 * Extracted from `ActiveWorkoutScreen.kt` (Milestone 8, implementation-review
 * findings #2/#3) to stay under detekt's per-file `TooManyFunctions`
 * threshold once the planned-target progress display and tracking-type-aware
 * field rendering landed - a cohesive unit (the per-exercise card and its
 * helpers), not an arbitrary split.
 *
 * The visual foundation reaches this surface through CP1's tokens and CP3's
 * primitives: the card is a [RepFlowCard], planned targets and logged sets
 * are status chips and circular markers, every number renders in CP1's
 * tabular numeric style so a changing digit never reflows a row, and the
 * optional RPE/pain/technique fields sit behind the design's own collapsible
 * detail disclosure. Every glyph comes from CP2's bounded local set.
 *
 * **Input affordances are deliberately unchanged.** The design draws weight
 * and reps as steppers and RPE/pain/technique as pill rows; this checkpoint
 * keeps them as text fields, because the plan's own preserved invariants
 * require both "their existing input affordances (only their visual chrome
 * changes)" and a still-green `ActiveWorkoutScreenTest`, which types into the
 * load field. Load is decimal and unbounded, so a stepper would also need a
 * step size neither the design nor the plan states - an invented product
 * decision, not a reskin. See `docs/ACTIVE_MILESTONE.md` for the full note.
 */
@Composable
internal fun ExerciseCard(
    exercise: ActiveExerciseUi,
    onRecordSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
    onUndoLastSet: (WorkoutExerciseId) -> Unit,
    onEditLastSet: (WorkoutExerciseId, Double?, Int?, Int?, Double?, Boolean, Int?, Int?) -> Unit,
) {
    var loadText by remember(exercise.id) { mutableStateOf("") }
    var repsText by remember(exercise.id) { mutableStateOf("") }
    var durationText by remember(exercise.id) { mutableStateOf("") }
    var rpeText by remember(exercise.id) { mutableStateOf("") }
    var isWarmup by remember(exercise.id) { mutableStateOf(false) }
    var painText by remember(exercise.id) { mutableStateOf("") }
    var techniqueQualityText by remember(exercise.id) { mutableStateOf("") }
    var detailExpanded by remember(exercise.id) { mutableStateOf(false) }

    fun clearEntryFields() {
        loadText = ""
        repsText = ""
        durationText = ""
        rpeText = ""
        isWarmup = false
        painText = ""
        techniqueQualityText = ""
    }

    val trackingTypeLabel =
        stringResource(
            when (exercise.trackingType) {
                ExerciseTrackingType.WEIGHT_AND_REPS -> R.string.exercise_tracking_type_weight_and_reps
                ExerciseTrackingType.REPS_ONLY -> R.string.exercise_tracking_type_reps_only
                ExerciseTrackingType.DURATION -> R.string.exercise_tracking_type_duration
            },
        )

    RepFlowCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RepFlowSpacing.screenPadding, vertical = RepFlowSpacing.gapSm),
        contentPadding = PaddingValues(RepFlowSpacing.cardPaddingMin),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // The design's own type note, carrying its `ph-info` glyph. The
                // three labels ("Weight & reps"/"Reps only"/"Duration") are all
                // distinct from the entry-field labels below, so this chip never
                // shadows a node ActiveWorkoutScreenTest resolves by exact text.
                RepFlowStatusChip(
                    text = trackingTypeLabel,
                    tone = RepFlowTagTone.Outline,
                    icon = RepFlowIcons.info,
                )
            }
            exercise.plannedTarget?.let { target -> PlannedTargetSummary(target, exercise.sets) }
            if (exercise.sets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
                    for ((set, isExtra) in exercise.setsWithExtraFlag()) {
                        LoggedSetRow(set, exercise.trackingType, isExtra)
                    }
                }
            }
            TrackingTypeEntryFields(
                trackingType = exercise.trackingType,
                loadText = loadText,
                onLoadChange = { loadText = it },
                repsText = repsText,
                onRepsChange = { repsText = it },
                durationText = durationText,
                onDurationChange = { durationText = it },
            )
            WarmupToggleRow(isWarmup = isWarmup, onWarmupChange = { isWarmup = it })
            SetDetailSection(
                expanded = detailExpanded,
                onExpandedChange = { detailExpanded = it },
                rpeText = rpeText,
                onRpeChange = { rpeText = it },
                painText = painText,
                onPainChange = { painText = it },
                techniqueQualityText = techniqueQualityText,
                onTechniqueQualityChange = { techniqueQualityText = it },
            )
            EntryActions(
                hasRecordedSets = exercise.sets.isNotEmpty(),
                onAddSet = {
                    onRecordSet(
                        exercise.id,
                        loadText.toDoubleOrNull(),
                        repsText.toIntOrNull(),
                        durationText.toIntOrNull(),
                        rpeText.toDoubleOrNull(),
                        isWarmup,
                        painText.toIntOrNull(),
                        techniqueQualityText.toIntOrNull(),
                    )
                    // Cleared immediately (Milestone 8, implementation-review finding #3):
                    // a deliberate choice, not an oversight - each entry row starts fresh
                    // for the next set rather than risking an accidental duplicate submit.
                    clearEntryFields()
                },
                onUndoLastSet = { onUndoLastSet(exercise.id) },
                onEditLastSet = {
                    onEditLastSet(
                        exercise.id,
                        loadText.toDoubleOrNull(),
                        repsText.toIntOrNull(),
                        durationText.toIntOrNull(),
                        rpeText.toDoubleOrNull(),
                        isWarmup,
                        painText.toIntOrNull(),
                        techniqueQualityText.toIntOrNull(),
                    )
                    clearEntryFields()
                },
            )
        }
    }
}

/**
 * Warm-up/working-set progress, target rep/duration range, and planned rest -
 * resolved from the plan this exercise was seeded from (Milestone 8,
 * implementation-review finding #2), now drawn as the design's status chips.
 *
 * Progress and target are split across two rows rather than one flowing row:
 * four chips on one line overflow a 360dp screen, and a clipped chip is both
 * unreadable and invisible to the device test's `assertIsDisplayed`. Every
 * string, and the condition that decides whether it appears at all, is
 * unchanged.
 */
@Composable
private fun PlannedTargetSummary(
    target: PlannedTargetUi,
    sets: List<ActiveSetUi>,
) {
    val warmupDone = sets.count { it.isWarmup }
    val workingDone = sets.count { !it.isWarmup }
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
            target.targetWarmupSets?.let { targetWarmup ->
                RepFlowStatusChip(
                    text = stringResource(R.string.workout_active_plan_warmup_progress, warmupDone, targetWarmup),
                    tone = if (warmupDone >= targetWarmup) RepFlowTagTone.Done else RepFlowTagTone.Pending,
                    icon = RepFlowIcons.fire,
                )
            }
            RepFlowStatusChip(
                text =
                    stringResource(
                        R.string.workout_active_plan_working_progress,
                        workingDone,
                        target.targetWorkingSets,
                    ),
                tone = if (workingDone >= target.targetWorkingSets) RepFlowTagTone.Done else RepFlowTagTone.Pending,
            )
        }
        if (target.repRange != null || target.durationRangeSeconds != null || target.restSeconds != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
                target.repRange?.let { range ->
                    RepFlowStatusChip(
                        text = stringResource(R.string.workout_active_plan_rep_range, range.first, range.last),
                    )
                }
                target.durationRangeSeconds?.let { range ->
                    RepFlowStatusChip(
                        text = stringResource(R.string.workout_active_plan_duration_range, range.first, range.last),
                    )
                }
                target.restSeconds?.let { rest ->
                    RepFlowStatusChip(text = stringResource(R.string.workout_active_plan_rest, rest))
                }
            }
        }
    }
}

/**
 * One already-recorded set: the design's 26dp circular status marker beside
 * the row's own summary.
 *
 * Every field shown here (Milestone 8, implementation-review finding #3) is
 * one [com.repflow.app.domain.workout.WorkoutSet.create] actually accepts
 * for a set of [trackingType] - no `?: 0`/`?: 0.0` standing in for a field
 * that's genuinely absent (e.g. an untracked `load` on a bodyweight
 * `WEIGHT_AND_REPS` set falls back to the reps-only row instead of
 * claiming "0.0 kg").
 *
 * The summary line stays one node carrying exactly `primary + warm-up +
 * extra`: `ActiveWorkoutScreenTest` matches that concatenation by exact
 * text, so the two suffixes cannot be promoted into chips of their own. The
 * marker repeats the set number the summary already states, so it is hidden
 * from the accessibility tree rather than announced twice.
 */
@Composable
private fun LoggedSetRow(
    set: ActiveSetUi,
    trackingType: ExerciseTrackingType,
    isExtra: Boolean,
) {
    val detail =
        listOfNotNull(
            set.rpe?.let { rpe -> stringResource(R.string.workout_active_set_rpe, rpe.toString()) },
            set.pain?.let { pain -> stringResource(R.string.workout_active_set_pain, pain) },
            set.techniqueQuality?.let { quality ->
                stringResource(R.string.workout_active_set_technique_quality, quality)
            },
        )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Box(
            modifier =
                Modifier
                    .size(SetMarkerSize)
                    .clip(CircleShape)
                    .background(color = RepFlowColor.control, shape = CircleShape)
                    .border(BorderStroke(SetMarkerBorderWidth, RepFlowColor.hairline), CircleShape)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (set.isWarmup) {
                Icon(
                    painter = painterResource(RepFlowIcons.fire),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SetMarkerIconSize),
                )
            } else {
                Text(
                    text = set.setNumber.toString(),
                    style =
                        RepFlowNumericTextStyle.copy(
                            fontSize = SetMarkerFontSize,
                            lineHeight = SetMarkerLineHeight,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
        ) {
            Text(
                text = loggedSetSummary(set, trackingType, isExtra),
                style =
                    RepFlowNumericTextStyle.copy(
                        fontSize = SetRowFontSize,
                        lineHeight = SetRowLineHeight,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail.joinToString(separator = " · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One logged set's summary line, unchanged from before this checkpoint:
 * `primary + warm-up suffix + extra suffix`, assembled into a single string.
 *
 * It stays one string, and therefore one text node, because
 * `ActiveWorkoutScreenTest` matches exactly that concatenation - promoting
 * either suffix into a chip of its own would leave the row looking right and
 * the test resolving nothing.
 */
@Composable
private fun loggedSetSummary(
    set: ActiveSetUi,
    trackingType: ExerciseTrackingType,
    isExtra: Boolean,
): String {
    val warmupSuffix = if (set.isWarmup) " " + stringResource(R.string.workout_active_warmup_suffix) else ""
    val extraSuffix = if (isExtra) " " + stringResource(R.string.workout_active_set_extra_suffix) else ""
    val primaryText =
        when {
            trackingType == ExerciseTrackingType.DURATION -> {
                stringResource(R.string.workout_active_set_row_duration, set.setNumber, set.durationSeconds ?: 0)
            }

            trackingType == ExerciseTrackingType.WEIGHT_AND_REPS && set.load != null -> {
                stringResource(R.string.workout_active_set_row_weight_reps, set.setNumber, set.load, set.reps ?: 0)
            }

            else -> {
                stringResource(R.string.workout_active_set_row_reps, set.setNumber, set.reps ?: 0)
            }
        }
    return primaryText + warmupSuffix + extraSuffix
}

/**
 * Only the field(s) the exercise's tracking type actually persists are shown -
 * `WorkoutSet.create` rejects reps/load for a DURATION exercise and rejects
 * durationSeconds for the other two (Milestone 8, implementation-review
 * finding #3). Unchanged; only the fields' own chrome moves.
 */
@Composable
private fun TrackingTypeEntryFields(
    trackingType: ExerciseTrackingType,
    loadText: String,
    onLoadChange: (String) -> Unit,
    repsText: String,
    onRepsChange: (String) -> Unit,
    durationText: String,
    onDurationChange: (String) -> Unit,
) {
    when (trackingType) {
        ExerciseTrackingType.WEIGHT_AND_REPS -> {
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
                NumericEntryField(
                    value = loadText,
                    onValueChange = onLoadChange,
                    label = stringResource(R.string.workout_active_load_label),
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                NumericEntryField(
                    value = repsText,
                    onValueChange = onRepsChange,
                    label = stringResource(R.string.workout_active_reps_label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        ExerciseTrackingType.REPS_ONLY -> {
            NumericEntryField(
                value = repsText,
                onValueChange = onRepsChange,
                label = stringResource(R.string.workout_active_reps_label),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ExerciseTrackingType.DURATION -> {
            NumericEntryField(
                value = durationText,
                onValueChange = onDurationChange,
                label = stringResource(R.string.workout_active_duration_label),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The one entry-field shape this pad uses, so seven near-identical
 * `OutlinedTextField` blocks become one.
 *
 * The value renders in CP1's tabular numeric style at entry size: this is the
 * surface the plan names for it, and a load that changes width as its digits
 * change is exactly what tabular figures exist to prevent.
 */
@Composable
private fun NumericEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle =
            RepFlowNumericTextStyle.copy(
                fontSize = EntryValueFontSize,
                lineHeight = EntryValueLineHeight,
            ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

/**
 * The warm-up flag, behind the design's own `ph-fire` glyph.
 *
 * It stays a Material 3 [Switch] rather than becoming the design's pill
 * toggle: the plan preserves this field's input affordance, and a `Switch`
 * is also the only one of the two that already announces its on/off state.
 */
@Composable
private fun WarmupToggleRow(
    isWarmup: Boolean,
    onWarmupChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = ControlRowMinHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.fire),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(FieldIconSize),
        )
        Text(
            text = stringResource(R.string.workout_active_warmup_label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = isWarmup, onCheckedChange = onWarmupChange)
    }
}

/**
 * The design's collapsible detail section: the three optional fields
 * (RPE, pain, technique quality) behind one disclosure, so the fast path
 * through this pad is load/reps and one button.
 *
 * Collapsing only hides the fields - the values live in [ExerciseCard]'s own
 * state and are still submitted, and `clearEntryFields` still resets them
 * after every recorded set, so nothing can be carried into the next set
 * unseen.
 */
@Composable
private fun SetDetailSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    rpeText: String,
    onRpeChange: (String) -> Unit,
    painText: String,
    onPainChange: (String) -> Unit,
    techniqueQualityText: String,
    onTechniqueQualityChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = ControlRowMinHeight)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button) { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.sliders),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(FieldIconSize),
            )
            Text(
                text = stringResource(R.string.workout_active_set_detail_toggle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(if (expanded) RepFlowIcons.caretUp else RepFlowIcons.caretDown),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(FieldIconSize),
            )
        }
        if (expanded) {
            NumericEntryField(
                value = rpeText,
                onValueChange = onRpeChange,
                label = stringResource(R.string.workout_active_rpe_label),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
                NumericEntryField(
                    value = painText,
                    onValueChange = onPainChange,
                    label = stringResource(R.string.workout_active_pain_label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
                NumericEntryField(
                    value = techniqueQualityText,
                    onValueChange = onTechniqueQualityChange,
                    label = stringResource(R.string.workout_active_technique_quality_label),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One primary action, with undo/edit as the quietest tier beneath it - the
 * design's own "one primary action" rule, applied to this card.
 *
 * The two secondary actions keep their words as well as gaining the design's
 * glyphs: an icon-only undo on the surface that writes workout data is not
 * an affordance worth guessing at.
 */
@Composable
private fun EntryActions(
    hasRecordedSets: Boolean,
    onAddSet: () -> Unit,
    onUndoLastSet: () -> Unit,
    onEditLastSet: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
        RepFlowPrimaryButton(
            text = stringResource(R.string.workout_active_add_set),
            onClick = onAddSet,
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasRecordedSets) {
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.workout_active_undo_set),
                    onClick = onUndoLastSet,
                    modifier = Modifier.weight(1f),
                    leadingIcon = RepFlowIcons.arrowCounterClockwise,
                )
                RepFlowNeutralOutlineButton(
                    text = stringResource(R.string.workout_active_edit_set),
                    onClick = onEditLastSet,
                    modifier = Modifier.weight(1f),
                    leadingIcon = RepFlowIcons.pencilSimple,
                )
            }
        }
    }
}

/**
 * Pairs each set with whether it falls beyond its planned warm-up/working
 * quota (Milestone 8, implementation-review finding #2) - `false` for every
 * set on an ad-hoc exercise ([ActiveExerciseUi.plannedTarget] is `null`).
 *
 * `internal` rather than private so a plain-JVM test can reach it: it is the
 * one piece of this file that decides content rather than appearance.
 */
internal fun ActiveExerciseUi.setsWithExtraFlag(): List<Pair<ActiveSetUi, Boolean>> {
    val target = plannedTarget ?: return sets.map { it to false }
    var warmupSeen = 0
    var workingSeen = 0
    return sets.map { set ->
        val isExtra =
            if (set.isWarmup) {
                warmupSeen += 1
                warmupSeen > (target.targetWarmupSets ?: 0)
            } else {
                workingSeen += 1
                workingSeen > target.targetWorkingSets
            }
        set to isExtra
    }
}

/** The design's own logged-set status marker: a 26dp circle. */
private val SetMarkerSize = 26.dp

private val SetMarkerBorderWidth = 1.dp

private val SetMarkerIconSize = 14.dp

private val SetMarkerFontSize = 12.sp

private val SetMarkerLineHeight = 16.sp

/** Body size, tabular: a set list whose digits change must not reflow. */
private val SetRowFontSize = 15.sp

private val SetRowLineHeight = 22.sp

/** Entry-field value size - larger than body, still short of the 24sp pad value. */
private val EntryValueFontSize = 20.sp

private val EntryValueLineHeight = 26.sp

/** In-row glyphs: the type note, the warm-up flame, the detail sliders/caret. */
private val FieldIconSize = 20.dp

/**
 * The design's own floor for anything tappable, applied to the two rows that
 * draw their own tap target rather than inheriting a component's.
 */
internal val ControlRowMinHeight = 44.dp
