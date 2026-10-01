package com.repflow.app.presentation.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.history.WorkoutSummary
import com.repflow.app.application.trainingplan.TrainingPlanVersionLabel
import com.repflow.app.domain.workout.WorkoutExercise
import com.repflow.app.domain.workout.WorkoutSet
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowStat
import com.repflow.app.presentation.designsystem.components.RepFlowStatRow
import com.repflow.app.presentation.designsystem.components.RepFlowStatusChip
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import com.repflow.app.presentation.designsystem.components.RepFlowTagTone
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.durationMinutes
import com.repflow.app.presentation.workout.RecapDelta
import com.repflow.app.presentation.workout.deltaText
import com.repflow.app.presentation.workout.isGain
import com.repflow.app.presentation.workout.recapDelta
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Read-only detail of one completed workout (remediation-1 CP12, `3b` and
 * `3a`'s live detail): CP3's sub-screen bar with a `⋮` that invalidates the
 * workout behind a destructive confirmation (`3a`, `askInvalidate`); the
 * day and times; the workout's name; `<plan> · version N`; the `Time` /
 * `Volume` / `Sets` tiles; then one block per exercise - its name, the
 * warm-up count, the change against the last time it was trained (with a
 * medal for a personal best), and every logged set.
 *
 * An exercise with no sets reads `No sets logged`, with no change shown: the
 * design's `skipped` label is not derivable (`D20`). Sets are read-only - the
 * design's edit pencil is blocked by an open decision (`D7`) - and there is no
 * session note (`D3`).
 */
@Composable
fun HistoryDetailScreen(
    summary: WorkoutSummary,
    planLabel: TrainingPlanVersionLabel?,
    onBackClick: () -> Unit,
    onInvalidateConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHost: @Composable () -> Unit = {},
) {
    val session = summary.session
    var confirmingInvalidation by rememberSaveable { mutableStateOf(false) }
    RepFlowScreenScaffold(
        title = "",
        modifier = modifier,
        onBack = onBackClick,
        backContentDescription = stringResource(R.string.history_detail_back),
        actions = {
            if (!session.isInvalidated) {
                InvalidateAction(onClick = { confirmingInvalidation = true })
            }
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        val locale = LocalConfiguration.current.locales[0]
        val zone = ZoneId.systemDefault()
        val medalExercises = if (session.isInvalidated) emptySet() else summary.personalBests.map { it.exerciseId }.toSet()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item(key = "header") {
                DetailHeader(summary = summary, planLabel = planLabel, locale = locale, zone = zone)
            }
            if (summary.recaps.isEmpty()) {
                item(key = "no-exercises") {
                    RepFlowCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.history_detail_no_exercises),
                            style = MaterialTheme.typography.bodyMedium,
                            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                        )
                    }
                }
            }
            items(items = summary.recaps, key = { it.exercise.id.value }) { recap ->
                ExerciseBlock(
                    exercise = recap.exercise,
                    delta = recapDelta(recap.best, recap.lastTime),
                    isPersonalBest = recap.exercise.exerciseId in medalExercises,
                )
            }
        }
    }

    if (confirmingInvalidation) {
        InvalidateWorkoutDialog(
            onConfirm = {
                confirmingInvalidation = false
                onInvalidateConfirmed()
            },
            onDismiss = { confirmingInvalidation = false },
        )
    }
}

/** `3a`'s `⋮` (`:1895`): its one action is invalidating, so that is its accessible name. */
@Composable
private fun InvalidateAction(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .size(ActionSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(RepFlowIcons.dotsThreeVertical),
            contentDescription = stringResource(R.string.history_session_invalidate_action),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = ACTION_ICON_ALPHA),
            modifier = Modifier.size(ActionIconSize),
        )
    }
}

@Composable
private fun DetailHeader(
    summary: WorkoutSummary,
    planLabel: TrainingPlanVersionLabel?,
    locale: Locale,
    zone: ZoneId,
) {
    val session = summary.session
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val endedAt = checkNotNull(session.endedAt)
    val started = session.startedAt.atZone(zone)
    val dayPattern = if (started.year == LocalDate.now(zone).year) DAY_PATTERN else DAY_WITH_YEAR_PATTERN
    val time = DateTimeFormatter.ofPattern(TIME_PATTERN, locale)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text =
                listOf(
                    DateTimeFormatter.ofPattern(dayPattern, locale).format(started),
                    time.format(started) + TIME_ARROW + time.format(endedAt.atZone(zone)),
                ).joinToString(META_SEPARATOR).uppercase(locale),
            style = MaterialTheme.typography.labelSmall,
            color = secondary,
        )
        Text(
            text = planLabel?.planName ?: stringResource(R.string.home_untitled_workout),
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontSize = TitleFontSize,
                    lineHeight = TitleLineHeight,
                    fontWeight = FontWeight.Medium,
                ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).semantics { heading() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
        ) {
            Text(
                text =
                    planLabel?.let { stringResource(R.string.history_detail_plan_line, it.planName, it.versionNumber) }
                        ?: stringResource(R.string.history_detail_no_plan),
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
            )
            if (session.isInvalidated) {
                RepFlowStatusChip(
                    text = stringResource(R.string.history_row_badge_invalidated),
                    tone = RepFlowTagTone.Pending,
                    icon = RepFlowIcons.prohibit,
                )
            }
        }
        RepFlowStatRow(
            stats =
                listOf(
                    RepFlowStat(
                        label = stringResource(R.string.history_detail_stat_time),
                        value = stringResource(R.string.history_duration_minutes, durationMinutes(session.startedAt, endedAt)),
                    ),
                    RepFlowStat(
                        label = stringResource(R.string.history_detail_stat_volume),
                        value =
                            volumeKg(session)?.let { NumberFormat.getIntegerInstance(locale).format(it) }
                                ?: stringResource(R.string.history_volume_none),
                    ),
                    RepFlowStat(label = stringResource(R.string.history_detail_stat_sets), value = workingSetCount(session).toString()),
                ),
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
    }
}

/** One exercise (`3b`, `:1908-1931`): name, warm-up count and change, then its sets - or `No sets logged`. */
@Composable
private fun ExerciseBlock(
    exercise: WorkoutExercise,
    delta: RecapDelta,
    isPersonalBest: Boolean,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val warmups = exercise.sets.count { it.isWarmup }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.exerciseNameSnapshot,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = ExerciseNameFontSize),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (warmups > 0) {
                        Text(
                            text = pluralStringResource(R.plurals.history_detail_warmup_count, warmups, warmups),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = WarmupFontSize),
                            color = secondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (delta != RecapDelta.NoWorkingSets) {
                    DeltaLabel(delta = delta, isPersonalBest = isPersonalBest)
                }
            }
            if (exercise.sets.isEmpty()) {
                Text(
                    text = stringResource(R.string.history_detail_no_sets),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = NoSetsFontSize),
                    color = secondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val numbers = workingSetNumbers(exercise.sets)
            exercise.sets.forEachIndexed { index, set ->
                SetRow(exercise = exercise, set = set, number = numbers[index])
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
        )
    }
}

@Composable
private fun DeltaLabel(
    delta: RecapDelta,
    isPersonalBest: Boolean,
) {
    val color =
        if (delta.isGain()) {
            repFlowAccentOutlineColors(MaterialTheme.colorScheme).label
        } else {
            repFlowSecondaryTextColor(MaterialTheme.colorScheme)
        }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (isPersonalBest) {
            Icon(
                painter = painterResource(RepFlowIcons.medalFill),
                contentDescription = stringResource(R.string.history_detail_personal_best_content_description),
                tint = color,
                modifier = Modifier.size(MedalSize),
            )
        }
        Text(
            text = deltaText(delta),
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * `3b`'s set row: the working-set number, what was logged (by tracking type),
 * the RPE, and - on a line of their own - pain and technique when recorded. A
 * warm-up is unnumbered and marked `warm-up`.
 */
@Composable
private fun SetRow(
    exercise: WorkoutExercise,
    set: WorkoutSet,
    number: Int?,
) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = SetFontSize, fontFeatureSettings = "tnum")
    val metaStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    Column(modifier = Modifier.fillMaxWidth().padding(start = 2.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = number?.toString().orEmpty(),
                style = metaStyle.copy(fontSize = SetNumberFontSize),
                color = secondary,
                modifier = Modifier.width(SetNumberWidth),
            )
            Text(
                text = setValueText(historySetValueOf(exercise.trackingType, set)),
                style = valueStyle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (set.isWarmup) WARMUP_VALUE_ALPHA else SET_VALUE_ALPHA),
            )
            set.rpe?.let { rpe ->
                Text(text = stringResource(R.string.history_detail_set_rpe, rpeText(rpe)), style = valueStyle, color = secondary)
            }
            if (set.isWarmup) {
                Text(text = stringResource(R.string.history_detail_set_warmup_suffix), style = metaStyle, color = secondary)
            }
        }
        if (set.pain != null || set.techniqueQuality != null) {
            Row(
                modifier = Modifier.padding(start = SetNumberWidth + 12.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
            ) {
                set.pain?.let { pain ->
                    Text(text = stringResource(R.string.history_detail_set_pain, pain), style = metaStyle, color = secondary)
                }
                set.techniqueQuality?.let { quality ->
                    Text(
                        text = stringResource(R.string.history_detail_set_technique_quality, quality),
                        style = metaStyle,
                        color = secondary,
                    )
                }
            }
        }
    }
}

/** `70 kg × 10`, `70 kg`, `12 reps`, `45 s`, or `—`. */
@Composable
private fun setValueText(value: HistorySetValue): String =
    when (value) {
        is HistorySetValue.Load -> {
            val kg = RepFlowStepperMath.format(value.kg)
            value.reps?.let { stringResource(R.string.history_detail_set_load, kg, it) }
                ?: stringResource(R.string.history_detail_set_load_only, kg)
        }

        is HistorySetValue.Reps -> {
            pluralStringResource(R.plurals.history_detail_set_reps, value.reps, value.reps)
        }

        is HistorySetValue.Seconds -> {
            stringResource(R.string.history_detail_set_duration, value.seconds)
        }

        HistorySetValue.Empty -> {
            stringResource(R.string.history_volume_none)
        }
    }

/**
 * `Invalidate this workout?` (`3a`, `:1942-1954`) - `6b`'s one sanctioned
 * dialog, a destructive confirmation. `Keep it` is the safe way out; the
 * destructive action carries the `prohibit` glyph in the error tone. The copy
 * says how to see the workout again (`Show invalidated`) rather than the
 * design's "bring it back", which no use case does.
 */
@Composable
private fun InvalidateWorkoutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_invalidate_dialog_title)) },
        text = { Text(stringResource(R.string.history_invalidate_dialog_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(
                    painter = painterResource(RepFlowIcons.prohibit),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(DialogIconSize),
                )
                Text(
                    text = stringResource(R.string.history_invalidate_dialog_confirm),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.history_invalidate_dialog_cancel))
            }
        },
    )
}

/** `Sat 8 Aug`; a workout from another year carries it. */
private const val DAY_PATTERN = "EEE d MMM"
private const val DAY_WITH_YEAR_PATTERN = "EEE d MMM yyyy"
private const val TIME_PATTERN = "HH:mm"
private const val TIME_ARROW = " → "

private const val ACTION_ICON_ALPHA = 0.7f
private const val SET_VALUE_ALPHA = 0.9f
private const val WARMUP_VALUE_ALPHA = 0.6f

private val ActionSize = 44.dp
private val ActionIconSize = 20.dp
private val MedalSize = 12.dp
private val DialogIconSize = 16.dp
private val SetNumberWidth = 22.dp

/** `28/500` (`:1899`). */
private val TitleFontSize = 28.sp
private val TitleLineHeight = 32.sp
private val ExerciseNameFontSize = 15.5.sp
private val WarmupFontSize = 12.sp
private val NoSetsFontSize = 13.sp
private val SetFontSize = 14.5.sp
private val SetNumberFontSize = 12.sp
