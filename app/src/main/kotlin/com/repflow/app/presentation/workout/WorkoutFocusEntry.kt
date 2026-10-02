// Named for what it holds - focus mode's set-entry controls - rather than for
// its one class, the `SetEntryState` those controls write to.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.exercise.ExerciseTrackingType
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowPillPicker
import com.repflow.app.presentation.designsystem.components.RepFlowPillPickerDefaults
import com.repflow.app.presentation.designsystem.components.RepFlowPrimaryButton
import com.repflow.app.presentation.designsystem.components.RepFlowScaleRow
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.components.RepFlowStepper
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal

/*
 * Focus mode's set-entry controls (remediation-1 CP8, `4a` `nFocus`,
 * `RepFlow.dc.html:1286-1346`): the two stepper cards, the RPE / pain /
 * technique disclosure, the type note, the warm-up chip - and the correction
 * sheet a logged set's pencil opens (`:1549-1581`).
 *
 * **Steppers replace the text fields.** Weight steps by the exercise's own
 * load increment (`6b`), reps by 1, seconds by 5 (the prototype's own steps);
 * tapping a value opens CP3's keypad. **Scale rows replace the RPE, pain and
 * technique fields**, on the domain's own ranges (`D10`, `D11`).
 */

/**
 * What the user has dialled in for the next set. Held by focus mode rather
 * than by any one control, so the bottom bar's `Log set` reads it and the
 * disclosure can collapse without dropping anything; saved across a rotation.
 * [clear] runs after every logged set - the next set starts fresh, which is the
 * contract `ActiveWorkoutScreenTest.tappingAddSetClearsTheEntryFields` pins
 * (`D60`).
 */
@Stable
internal class SetEntryState {
    var load by mutableStateOf<BigDecimal?>(null)
    var reps by mutableStateOf<BigDecimal?>(null)
    var seconds by mutableStateOf<BigDecimal?>(null)
    var rpe by mutableStateOf<Int?>(null)
    var pain by mutableStateOf<Int?>(null)
    var technique by mutableStateOf<Int?>(null)
    var isWarmup by mutableStateOf(false)

    fun clear() {
        load = null
        reps = null
        seconds = null
        rpe = null
        pain = null
        technique = null
        isWarmup = false
    }

    companion object {
        val Saver: Saver<SetEntryState, Any> =
            listSaver(
                save = {
                    listOf(
                        it.load?.toPlainString(),
                        it.reps?.toPlainString(),
                        it.seconds?.toPlainString(),
                        it.rpe,
                        it.pain,
                        it.technique,
                        it.isWarmup,
                    )
                },
                restore = { saved ->
                    SetEntryState().apply {
                        load = (saved[INDEX_LOAD] as String?)?.toBigDecimal()
                        reps = (saved[INDEX_REPS] as String?)?.toBigDecimal()
                        seconds = (saved[INDEX_SECONDS] as String?)?.toBigDecimal()
                        rpe = saved[INDEX_RPE] as Int?
                        pain = saved[INDEX_PAIN] as Int?
                        technique = saved[INDEX_TECHNIQUE] as Int?
                        isWarmup = saved[INDEX_WARMUP] as Boolean
                    }
                },
            )

        private const val INDEX_LOAD = 0
        private const val INDEX_REPS = 1
        private const val INDEX_SECONDS = 2
        private const val INDEX_RPE = 3
        private const val INDEX_PAIN = 4
        private const val INDEX_TECHNIQUE = 5
        private const val INDEX_WARMUP = 6
    }
}

/**
 * The stepper cards for [trackingType]: `Weight` and `Reps` for weight & reps,
 * `Reps` alone for reps only, `Seconds` alone for a timed exercise - only the
 * fields `WorkoutSet.create` accepts for that type.
 *
 * `4a` sets the two cards side by side. Below [SideBySideMinWidth] they stack
 * instead: at a 360dp phone each half-width card leaves its value 39dp between
 * the two 44dp buttons, and a load such as `102.5` at 24sp needs about 70
 * (`D62`).
 */
@Suppress("LongParameterList")
@Composable
internal fun EntrySteppers(
    trackingType: ExerciseTrackingType,
    load: BigDecimal?,
    onLoadChange: (BigDecimal) -> Unit,
    reps: BigDecimal?,
    onRepsChange: (BigDecimal) -> Unit,
    seconds: BigDecimal?,
    onSecondsChange: (BigDecimal) -> Unit,
    loadStep: BigDecimal,
    repRange: IntRange?,
    durationRange: LongRange?,
    modifier: Modifier = Modifier,
) {
    val cards = mutableListOf<@Composable (Modifier) -> Unit>()
    if (trackingType == ExerciseTrackingType.WEIGHT_AND_REPS) {
        cards += { cardModifier -> WeightCard(load, onLoadChange, loadStep, cardModifier) }
    }
    if (trackingType == ExerciseTrackingType.DURATION) {
        cards += { cardModifier -> SecondsCard(seconds, onSecondsChange, durationRange, cardModifier) }
    } else {
        cards += { cardModifier -> RepsCard(reps, onRepsChange, repRange, cardModifier) }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (cards.size > 1 && maxWidth >= SideBySideMinWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
                cards.forEach { card -> card(Modifier.weight(1f)) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
                cards.forEach { card -> card(Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun WeightCard(
    load: BigDecimal?,
    onLoadChange: (BigDecimal) -> Unit,
    step: BigDecimal,
    modifier: Modifier,
) {
    val label = stringResource(R.string.workout_active_load_label)
    StepperCard(
        label = label,
        caption =
            stringResource(
                if (step.compareTo(BigDecimal.ONE) ==
                    0
                ) {
                    R.string.workout_focus_weight_caption_one
                } else {
                    R.string.workout_focus_weight_caption
                },
                RepFlowStepperMath.format(step),
            ),
        modifier = modifier,
    ) {
        RepFlowStepper(
            value = load,
            onValueChange = onLoadChange,
            step = step,
            keypadTitle = label,
            decrementContentDescription = stringResource(R.string.workout_focus_less_weight),
            incrementContentDescription = stringResource(R.string.workout_focus_more_weight),
            allowDecimal = true,
            emptyText = stringResource(R.string.workout_focus_value_empty),
        )
    }
}

@Composable
private fun RepsCard(
    reps: BigDecimal?,
    onRepsChange: (BigDecimal) -> Unit,
    repRange: IntRange?,
    modifier: Modifier,
) {
    val label = stringResource(R.string.workout_active_reps_label)
    StepperCard(
        label = label,
        caption = repRange?.let { stringResource(R.string.workout_focus_reps_target, it.first, it.last) },
        modifier = modifier,
    ) {
        RepFlowStepper(
            value = reps,
            onValueChange = onRepsChange,
            step = BigDecimal.ONE,
            keypadTitle = label,
            decrementContentDescription = stringResource(R.string.workout_focus_fewer_reps),
            incrementContentDescription = stringResource(R.string.workout_focus_more_reps),
            allowDecimal = false,
            emptyText = stringResource(R.string.workout_focus_value_empty),
        )
    }
}

@Composable
private fun SecondsCard(
    seconds: BigDecimal?,
    onSecondsChange: (BigDecimal) -> Unit,
    durationRange: LongRange?,
    modifier: Modifier,
) {
    val label = stringResource(R.string.workout_active_duration_label)
    StepperCard(
        label = label,
        caption =
            durationRange?.let { stringResource(R.string.workout_focus_seconds_target, it.first, it.last) }
                ?: stringResource(R.string.workout_focus_seconds_held),
        modifier = modifier,
    ) {
        RepFlowStepper(
            value = seconds,
            onValueChange = onSecondsChange,
            step = SECONDS_STEP,
            keypadTitle = label,
            decrementContentDescription = stringResource(R.string.workout_focus_fewer_seconds),
            incrementContentDescription = stringResource(R.string.workout_focus_more_seconds),
            allowDecimal = false,
            emptyText = stringResource(R.string.workout_focus_value_empty),
        )
    }
}

/** `4a`'s stepper card: radius 12, surface, hairline, padding 12/10; label, stepper, caption. */
@Composable
private fun StepperCard(
    label: String,
    caption: String?,
    modifier: Modifier,
    stepper: @Composable () -> Unit,
) {
    RepFlowCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = StepperCardHorizontalPadding, vertical = StepperCardVerticalPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
            FocusFieldLabel(text = label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            stepper()
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = CaptionFontSize),
                    color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * `6b`'s 11/500 uppercase label. Uppercased for display only - the semantics
 * keep the sentence-case words, as the prototype's CSS `text-transform` leaves
 * the text itself untouched, so a screen reader is handed `Weight`, not a
 * shouted `WEIGHT`.
 */
@Composable
internal fun FocusFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = text.uppercase(locale),
        style = MaterialTheme.typography.labelSmall,
        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        textAlign = textAlign,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
    )
}

/**
 * The detail disclosure (`:1310-1337`): `ph-sliders`, a summary of what is set
 * (or "RPE, pain, technique"), a caret; open, it reveals the three scale rows
 * and the line saying they apply to the next set only.
 *
 * Collapsing only hides the rows - the values live in [SetEntryState] and are
 * still submitted, then cleared once the set is logged, so nothing is carried
 * into the next set unseen.
 *
 * `clickable` merges the header's descendants into one node, and both glyphs
 * carry `contentDescription = null`, so without the `stateDescription` below
 * the header would read identically open and closed - the caret being the only
 * signal, and the caret being invisible to the semantics tree.
 */
@Composable
internal fun SetDetailSection(
    entry: SetEntryState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val stateLabel =
        stringResource(
            if (expanded) R.string.workout_active_set_detail_expanded else R.string.workout_active_set_detail_collapsed,
        )
    val summaryParts =
        listOfNotNull(
            entry.rpe?.let { stringResource(R.string.workout_focus_detail_rpe, it) },
            entry.pain?.let { stringResource(R.string.workout_focus_detail_pain, it) },
            entry.technique?.let { stringResource(R.string.workout_focus_detail_technique, it) },
        )
    val summary =
        summaryParts.takeIf { it.isNotEmpty() }?.joinToString(separator = " · ")
            ?: stringResource(R.string.workout_active_set_detail_toggle)
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = DisclosureMinHeight)
                    .clip(DisclosureShape)
                    .border(BorderStroke(1.dp, RepFlowColor.hairline), DisclosureShape)
                    .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                    .semantics { stateDescription = stateLabel }
                    .padding(horizontal = RepFlowSpacing.gapLg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.sliders),
                contentDescription = null,
                tint = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.size(DisclosureIconSize),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = DisclosureFontSize, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(if (expanded) RepFlowIcons.caretUp else RepFlowIcons.caretDown),
                contentDescription = null,
                tint = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.size(DisclosureCaretSize),
            )
        }
        if (expanded) {
            RpeScaleRow(selected = entry.rpe, onSelect = { entry.rpe = toggled(entry.rpe, it) })
            RepFlowScaleRow(
                options = ZERO_TO_FIVE,
                selectedIndex = entry.pain,
                onSelect = { entry.pain = toggled(entry.pain, it) },
                lowLabel = stringResource(R.string.workout_focus_pain_low),
                highLabel = stringResource(R.string.workout_focus_pain_high),
                label = stringResource(R.string.workout_active_pain_label),
            )
            RepFlowScaleRow(
                options = ZERO_TO_FIVE,
                selectedIndex = entry.technique,
                onSelect = { entry.technique = toggled(entry.technique, it) },
                lowLabel = stringResource(R.string.workout_focus_technique_low),
                highLabel = stringResource(R.string.workout_focus_technique_high),
                label = stringResource(R.string.workout_active_technique_quality_label),
            )
            Text(
                text = stringResource(R.string.workout_focus_detail_hint),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
        }
    }
}

/** Tapping the chosen cell again clears it, as the prototype's pickers do - every one of these is optional. */
private fun toggled(
    current: Int?,
    picked: Int,
): Int? = if (current == picked) null else picked

/**
 * RPE on the domain's whole `0..10` (`D11`), as `6b`'s scale row in two lines
 * - `0-5` over `6-10`. Eleven cells on one line would leave each about 25dp
 * wide at a 360dp phone, under `6b`'s 44 tap-target floor (`D64`). The second
 * line keeps the first line's cell width by leaving its sixth slot empty.
 */
@Composable
private fun RpeScaleRow(
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RepFlowPillPickerDefaults.scaleHeaderGap)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.workout_active_rpe_label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                    stringResource(
                        R.string.repflow_scale_end_labels,
                        stringResource(R.string.workout_focus_rpe_low),
                        stringResource(R.string.workout_focus_rpe_high),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
        }
        RepFlowPillPicker(
            options = ZERO_TO_FIVE,
            selectedIndex = selected?.takeIf { it < RPE_SECOND_LINE_START },
            onSelect = onSelect,
            sizing = RepFlowPillPickerDefaults.scale,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowPillPickerDefaults.scale.cellGap)) {
            RepFlowPillPicker(
                options = RPE_SECOND_LINE,
                selectedIndex = selected?.let { it - RPE_SECOND_LINE_START }?.takeIf { it >= 0 },
                onSelect = { onSelect(it + RPE_SECOND_LINE_START) },
                sizing = RepFlowPillPickerDefaults.scale,
                modifier = Modifier.weight(RPE_SECOND_LINE.size.toFloat()),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The warm-up chip (`:1344`), replacing the `Switch`: a 44 pill with the
 * flame, accent-tinted when on, and announced as a switch so its state is
 * never the tint alone. Its hint states the rest that follows - which is the
 * same planned (or default) rest for a warm-up as for a working set, because
 * that is the rule `onRecordSet` applies (`D63`).
 */
@Composable
internal fun WarmupChipRow(
    isWarmup: Boolean,
    onWarmupChange: (Boolean) -> Unit,
    restSeconds: Int,
) {
    val selected = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (isWarmup) selected.fill else Color.Transparent
    val border = if (isWarmup) selected.border else RepFlowColor.hairline
    val label = if (isWarmup) selected.label else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = ControlRowMinHeight)
                    .clip(RepFlowShapes.pill)
                    .background(color = fill, shape = RepFlowShapes.pill)
                    .border(BorderStroke(1.dp, border), RepFlowShapes.pill)
                    .toggleable(value = isWarmup, role = Role.Switch, onValueChange = onWarmupChange)
                    .padding(horizontal = WarmupChipHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WarmupChipGap),
        ) {
            Icon(
                painter = painterResource(RepFlowIcons.fire),
                contentDescription = null,
                tint = label,
                modifier = Modifier.size(WarmupChipIconSize),
            )
            Text(
                text = stringResource(R.string.workout_active_warmup_label),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = WarmupChipFontSize),
                color = label,
            )
        }
        Text(
            text =
                stringResource(
                    if (isWarmup) R.string.workout_focus_warmup_hint_on else R.string.workout_focus_warmup_hint_off,
                    restSeconds,
                ),
            style = MaterialTheme.typography.bodySmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            modifier = Modifier.weight(1f),
        )
    }
}

/** The domain's 0-5 pain and technique scales (`D10`). */
private const val SCALE_MAX = 5
private val ZERO_TO_FIVE = (0..SCALE_MAX).map(Int::toString)

/** RPE's second line starts at 6 and ends at the domain's 10 (`D11`, `D64`). */
private const val RPE_SECOND_LINE_START = SCALE_MAX + 1
private const val RPE_MAX = 10
private val RPE_SECOND_LINE = (RPE_SECOND_LINE_START..RPE_MAX).map(Int::toString)

/** The prototype's seconds step (`nRPlus` on a timed exercise). */
private const val SECONDS_STEP_VALUE = 5L

private val SECONDS_STEP = BigDecimal.valueOf(SECONDS_STEP_VALUE)

/**
 * Two stepper cards side by side need this much width before each value has
 * room for a five-character load at 24sp between its 44dp buttons (`D62`).
 */
private val SideBySideMinWidth = 400.dp

private val StepperCardHorizontalPadding = 10.dp
private val StepperCardVerticalPadding = 12.dp
private val CaptionFontSize = 11.5.sp

/** `min-height:48px`, `border-radius:10px`, the summary at 13. */
private val DisclosureMinHeight = 48.dp
private val DisclosureShape = RoundedCornerShape(10.dp)
private val DisclosureFontSize = 13.sp
private val DisclosureIconSize = 16.dp
private val DisclosureCaretSize = 14.dp

private val WarmupChipHorizontalPadding = 14.dp
private val WarmupChipGap = 7.dp
private val WarmupChipIconSize = 16.dp
private val WarmupChipFontSize = 13.sp

/**
 * The design's own floor for anything tappable, applied to the controls that
 * draw their own tap target rather than inheriting a component's.
 */
internal val ControlRowMinHeight = 44.dp
