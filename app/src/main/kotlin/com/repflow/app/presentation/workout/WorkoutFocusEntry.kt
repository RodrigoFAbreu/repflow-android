// Named for what it holds - focus mode's set-entry controls - rather than for
// its one class, the `SetEntryState` those controls write to.
@file:Suppress("MatchingDeclarationName")

package com.repflow.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
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
import com.repflow.app.presentation.designsystem.components.formatDuration
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
 * RPE, pain, technique, the warm-up flag and the clock's stopped time as they
 * stood when `Log set` was tapped: what [SetEntryState.clearAfterSet] may clear
 * once that save succeeds.
 */
internal data class SubmittedDetail(
    val rpe: Int?,
    val pain: Int?,
    val technique: Int?,
    val isWarmup: Boolean,
    val clockStoppedSeconds: Long?,
)

/**
 * What the user has dialled in for the next set. Held by focus mode rather
 * than by any one control, so the bottom bar's `Log set` reads it and the
 * disclosure can collapse without dropping anything; saved across a rotation.
 *
 * [clearAfterSet] runs once a logged set has saved (CP9, replacing `D60`):
 * weight, reps and seconds **stay** - the next set is usually the same numbers -
 * while RPE, pain, technique and the warm-up flag clear (`8d`'s rule). A failed
 * save clears nothing, so the set can be logged again. The entry stays editable
 * while the save runs, so each of those four clears only if it still holds what
 * was submitted ([submittedDetail]): a value changed since the tap was not saved.
 *
 * [touched] is "these numbers are the user's": they typed or stepped a value, or
 * saved a correction ([holdNumbers]). Until then the entry is *untouched* and takes
 * the seed ([applySeed]); once set it never takes one again. Saved state, so a
 * rotation or process death cannot turn a typed entry back into an untouched one;
 * [clearAfterSet] leaves it alone.
 *
 * The set clock (`20o`, `20p`) sits here because [stopClock] fills [seconds].
 * [clockStartedAtMillis] is a start moment, never a running count, so
 * [elapsedSeconds] comes from the wall clock each time it is read, and locking
 * the phone or a restore loses nothing. [clockStoppedSeconds] is the time it last
 * stopped at, until it starts again or a set saves. Like the rest of the entry,
 * neither is written to Room: they live in saved state only.
 */
@Stable
@Suppress("TooManyFunctions")
internal class SetEntryState {
    var load by mutableStateOf<BigDecimal?>(null)
        private set
    var reps by mutableStateOf<BigDecimal?>(null)
        private set
    var seconds by mutableStateOf<BigDecimal?>(null)
        private set
    var rpe by mutableStateOf<Int?>(null)
    var pain by mutableStateOf<Int?>(null)
    var technique by mutableStateOf<Int?>(null)
    var isWarmup by mutableStateOf(false)
    var touched by mutableStateOf(false)
        private set
    var clockStartedAtMillis by mutableStateOf<Long?>(null)
        private set
    var clockStoppedSeconds by mutableStateOf<Long?>(null)
        private set

    /** Whether [seconds] still holds the time the clock stopped at, rather than one changed since. */
    val filledFromClock: Boolean
        get() = clockStoppedSeconds?.let { seconds?.compareTo(BigDecimal.valueOf(it)) == 0 } ?: false

    /** The user typed or stepped the weight. */
    fun enterLoad(value: BigDecimal) {
        load = value
        touched = true
    }

    /** The user typed or stepped the reps. */
    fun enterReps(value: BigDecimal) {
        reps = value
        touched = true
    }

    /** The user typed or stepped the seconds. */
    fun enterSeconds(value: BigDecimal) {
        seconds = value
        touched = true
    }

    /**
     * Fills the numbers from [seed] when the entry is untouched; never marks it
     * touched, and never replaces a value the user entered.
     */
    fun applySeed(seed: SetEntrySeed?) {
        if (touched || seed == null) return
        load = seed.load
        reps = seed.reps
        seconds = seed.seconds
    }

    /**
     * Keeps the numbers as they are from now on, whatever later seeds say. `20n`
     * has a saved correction leave the steppers alone, and correcting the last
     * working set moves the seed. It sets [touched] because that is saved: the seed
     * effect restarts on a trip to the Board and on a restore.
     */
    fun holdNumbers() {
        touched = true
    }

    /** Starts the clock at [nowMillis] and forgets the last stop. A clock already running keeps its start moment. */
    fun startClock(nowMillis: Long) {
        if (clockStartedAtMillis != null) return
        clockStartedAtMillis = nowMillis
        clockStoppedSeconds = null
    }

    /**
     * The running clock's whole seconds at [nowMillis], `0` when none runs. Never
     * negative, because a wall clock set back can put [nowMillis] before the start.
     */
    fun elapsedSeconds(nowMillis: Long): Long {
        val start = clockStartedAtMillis ?: return 0
        return ((nowMillis - start) / MILLIS_PER_SECOND).coerceAtLeast(0)
    }

    /**
     * Stops the clock and fills [seconds] with the elapsed time, which also sets
     * [touched], so a later seed can't replace it. Under one second it only clears
     * the clock: `0:00` would claim a time that was never measured. The cap is there
     * because the value is later read with `toInt()`.
     */
    fun stopClock(nowMillis: Long) {
        val elapsed = elapsedSeconds(nowMillis).coerceAtMost(Int.MAX_VALUE.toLong())
        clockStartedAtMillis = null
        if (elapsed < 1) return
        enterSeconds(BigDecimal.valueOf(elapsed))
        clockStoppedSeconds = elapsed
    }

    /**
     * Whether `Log set` can succeed for [trackingType] (functional review GF-3,
     * design `8d`): a weight & reps exercise needs **both** a weight and reps -
     * `0` is an explicit weight and counts, an empty value does not; reps-only
     * needs reps and a timed exercise needs seconds. It gates logging only:
     * sets already stored with no weight stay valid. A running clock blocks it
     * whatever the type (`20o`), because the time isn't filled in until `Stop`.
     */
    fun canLog(trackingType: ExerciseTrackingType): Boolean =
        clockStartedAtMillis == null &&
            when (trackingType) {
                ExerciseTrackingType.DURATION -> (seconds?.signum() ?: 0) > 0
                ExerciseTrackingType.REPS_ONLY -> (reps?.signum() ?: 0) > 0
                ExerciseTrackingType.WEIGHT_AND_REPS -> load != null && (reps?.signum() ?: 0) > 0
            }

    fun submittedDetail(): SubmittedDetail = SubmittedDetail(rpe, pain, technique, isWarmup, clockStoppedSeconds)

    fun clearAfterSet(submitted: SubmittedDetail) {
        if (rpe == submitted.rpe) rpe = null
        if (pain == submitted.pain) pain = null
        if (technique == submitted.technique) technique = null
        if (isWarmup == submitted.isWarmup) isWarmup = false
        if (clockStoppedSeconds == submitted.clockStoppedSeconds) clockStoppedSeconds = null
    }

    /** The plain, bundle-safe form [Saver] and [FocusEntries.Saver] persist. New values go last: see [fromSaved]. */
    fun toSaved(): ArrayList<Any?> =
        arrayListOf(
            load?.toPlainString(),
            reps?.toPlainString(),
            seconds?.toPlainString(),
            rpe,
            pain,
            technique,
            isWarmup,
            touched,
            clockStartedAtMillis,
            clockStoppedSeconds,
        )

    companion object {
        val Saver: Saver<SetEntryState, Any> =
            listSaver(
                save = { it.toSaved() },
                restore = { saved -> fromSaved(saved) },
            )

        fun fromSaved(saved: List<Any?>): SetEntryState =
            SetEntryState().apply {
                load = (saved[INDEX_LOAD] as String?)?.toBigDecimal()
                reps = (saved[INDEX_REPS] as String?)?.toBigDecimal()
                seconds = (saved[INDEX_SECONDS] as String?)?.toBigDecimal()
                rpe = saved[INDEX_RPE] as Int?
                pain = saved[INDEX_PAIN] as Int?
                technique = saved[INDEX_TECHNIQUE] as Int?
                isWarmup = saved[INDEX_WARMUP] as Boolean
                touched = saved[INDEX_TOUCHED] as Boolean
                // A list saved by an older build ends before these, and restores with no clock.
                clockStartedAtMillis = saved.getOrNull(INDEX_CLOCK_STARTED) as Long?
                clockStoppedSeconds = saved.getOrNull(INDEX_CLOCK_STOPPED) as Long?
            }

        private const val INDEX_LOAD = 0
        private const val INDEX_REPS = 1
        private const val INDEX_SECONDS = 2
        private const val INDEX_RPE = 3
        private const val INDEX_PAIN = 4
        private const val INDEX_TECHNIQUE = 5
        private const val INDEX_WARMUP = 6
        private const val INDEX_TOUCHED = 7
        private const val INDEX_CLOCK_STARTED = 8
        private const val INDEX_CLOCK_STOPPED = 9
        private const val MILLIS_PER_SECOND = 1000L
    }
}

/**
 * Every exercise's unlogged [SetEntryState], held above focus mode (functional
 * review GF-1). Focus mode leaves composition when the Board shows and
 * re-keys on Next / Previous, so an entry remembered inside it was lost
 * with it; held here, each exercise keeps what was typed or stepped for as long
 * as the workout screen is up, and saved across a rotation. The seed rules are
 * unchanged: [entryFor] seeds an entry only when it is first created, and
 * [SetEntryState.applySeed] still never overwrites a touched one.
 */
@Stable
internal class FocusEntries(
    initial: Map<String, SetEntryState> = emptyMap(),
) {
    private val entries = HashMap(initial)

    /** The entry for [exerciseId], created (and seeded from [seed]) on first use. */
    fun entryFor(
        exerciseId: String,
        seed: SetEntrySeed?,
    ): SetEntryState = entries.getOrPut(exerciseId) { SetEntryState().also { it.applySeed(seed) } }

    companion object {
        val Saver: Saver<FocusEntries, Any> =
            mapSaver(
                save = { holder -> holder.entries.mapValues { (_, entry) -> entry.toSaved() } },
                restore = { saved ->
                    @Suppress("UNCHECKED_CAST")
                    FocusEntries(saved.mapValues { (_, value) -> SetEntryState.fromSaved(value as List<Any?>) })
                },
            )
    }
}

/**
 * The stepper cards for [trackingType]: `Weight` and `Reps` for weight & reps,
 * `Reps` alone for reps only, `Time (m:ss)` alone for a timed exercise - only the
 * fields `WorkoutSet.create` accepts for that type.
 *
 * `4a` sets the two cards side by side. Below [SideBySideMinWidth] they stack
 * instead: at a 360dp phone each half-width card leaves its value 39dp between
 * the two 44dp buttons, and a load such as `102.5` at 24sp needs about 70
 * (`D62`).
 *
 * The width decides only where they go, so it's one [Layout]. Choosing a `Row`
 * or a `Column` by width would give each its own saved state, so a phone that
 * crosses the width as it rotates would lose an open pad's entry and bring it
 * back later, out of date.
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
    val cardGap = RepFlowSpacing.gapMd
    Layout(
        content = {
            if (trackingType == ExerciseTrackingType.WEIGHT_AND_REPS) {
                WeightCard(load, onLoadChange, loadStep)
            }
            if (trackingType == ExerciseTrackingType.DURATION) {
                SecondsCard(seconds, onSecondsChange, durationRange)
            } else {
                RepsCard(reps, onRepsChange, repRange)
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { cards, constraints ->
        val gap = cardGap.roundToPx()
        val width = constraints.maxWidth
        val sideBySide = cards.size > 1 && width >= SideBySideMinWidth.roundToPx()
        val cardWidth = if (sideBySide) (width - gap) / 2 else width
        val placeables =
            cards.map { it.measure(constraints.copy(minWidth = cardWidth, maxWidth = cardWidth, minHeight = 0)) }
        val height =
            if (sideBySide) {
                placeables.maxOf { it.height }
            } else {
                placeables.sumOf { it.height } + gap * (placeables.size - 1)
            }
        layout(width, height) {
            var x = 0
            var y = 0
            placeables.forEach { card ->
                card.placeRelative(x, y)
                if (sideBySide) x += cardWidth + gap else y += card.height + gap
            }
        }
    }
}

@Composable
private fun WeightCard(
    load: BigDecimal?,
    onLoadChange: (BigDecimal) -> Unit,
    step: BigDecimal,
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
) {
    val label = stringResource(R.string.workout_active_reps_label)
    StepperCard(
        label = label,
        caption = repRange?.let { stringResource(R.string.workout_focus_reps_target, it.first, it.last) },
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
) {
    val label = stringResource(R.string.workout_active_duration_label)
    StepperCard(
        label = label,
        caption =
            durationRange?.let {
                stringResource(
                    R.string.workout_focus_seconds_target,
                    formatDuration(it.first),
                    formatDuration(it.last),
                )
            },
        hint = stringResource(R.string.workout_focus_time_hint, formatDuration(SECONDS_STEP.toLong())),
    ) {
        RepFlowStepper(
            value = seconds,
            onValueChange = onSecondsChange,
            step = SECONDS_STEP,
            keypadTitle = label,
            decrementContentDescription = stringResource(R.string.workout_focus_fewer_seconds),
            incrementContentDescription = stringResource(R.string.workout_focus_more_seconds),
            allowDecimal = false,
            durationEntry = true,
            emptyText = stringResource(R.string.workout_focus_value_empty),
            format = { formatDuration(it.toLong()) },
        )
    }
}

/** `4a`'s stepper card: radius 12, surface, hairline, padding 12/10; label, stepper, caption, then `20g`'s hint. */
@Composable
private fun StepperCard(
    label: String,
    caption: String?,
    hint: String? = null,
    stepper: @Composable () -> Unit,
) {
    RepFlowCard(
        contentPadding = PaddingValues(horizontal = StepperCardHorizontalPadding, vertical = StepperCardVerticalPadding),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm)) {
            FocusFieldLabel(text = label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            stepper()
            listOfNotNull(caption, hint).forEach { line ->
                Text(
                    text = line,
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
 * [showHeader] `false` (`Extra set fields` set to `Always shown`) draws the rows
 * alone, with no disclosure to toggle; pass [expanded] `true` with it.
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
    showHeader: Boolean = true,
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
        if (showHeader) {
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
        }
        if (expanded) {
            SetDetailRows(
                rpe = entry.rpe,
                pain = entry.pain,
                technique = entry.technique,
                onRpeChange = { entry.rpe = it },
                onPainChange = { entry.pain = it },
                onTechniqueChange = { entry.technique = it },
            )
            Text(
                text = stringResource(R.string.workout_focus_detail_hint),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
        }
    }
}

/**
 * RPE, pain and technique as scale rows, shared by logging ([SetDetailSection])
 * and the correction sheet (`20h` and `20i` draw the same rows). Each callback
 * gets the row's new value, or `null` when the chosen cell was tapped again.
 */
@Composable
internal fun SetDetailRows(
    rpe: Int?,
    pain: Int?,
    technique: Int?,
    onRpeChange: (Int?) -> Unit,
    onPainChange: (Int?) -> Unit,
    onTechniqueChange: (Int?) -> Unit,
) {
    // Tapping the chosen cell again clears it, as the prototype's pickers do - every one of these is optional.
    fun toggled(
        current: Int?,
        picked: Int,
    ): Int? = if (current == picked) null else picked

    Column(verticalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg)) {
        RpeScaleRow(selected = rpe, onSelect = { onRpeChange(toggled(rpe, it)) })
        RepFlowScaleRow(
            options = ZERO_TO_FIVE,
            selectedIndex = pain,
            onSelect = { onPainChange(toggled(pain, it)) },
            lowLabel = stringResource(R.string.workout_focus_pain_low),
            highLabel = stringResource(R.string.workout_focus_pain_high),
            label = stringResource(R.string.workout_active_pain_label),
        )
        RepFlowScaleRow(
            options = ZERO_TO_FIVE,
            selectedIndex = technique,
            onSelect = { onTechniqueChange(toggled(technique, it)) },
            lowLabel = stringResource(R.string.workout_focus_technique_low),
            highLabel = stringResource(R.string.workout_focus_technique_high),
            label = stringResource(R.string.workout_active_technique_quality_label),
        )
    }
}

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
                    formatDuration(restSeconds.toLong()),
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
