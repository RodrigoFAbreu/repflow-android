package com.repflow.app.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowAccentOutlineButton
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/*
 * Home's `Last workout` and history-error cards (remediation-1 CP5), and the
 * values every Home card shares. Each card is transcribed from `4a`
 * (`RepFlow.dc.html:738-822`) or `1d` (`:2978-3036`); the resume, start and
 * recovery cards have files of their own.
 */

internal val CardPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
internal val CardActionsGap = 8.dp
internal val SmallGlyphSize = 13.dp
private val EmptyStateGlyphSize = 26.dp
private const val EMPTY_GLYPH_ALPHA = 0.35f

/** The resume card's row height: `Resume` is the 56dp primary tier, and its neighbours match it. */
internal val ActionRowMinHeight = 56.dp

/** `1d`'s empty-state body: a 26dp glyph at 35% over a centred 13.5 line. */
@Composable
internal fun ColumnScope.EmptyStateBody(
    glyph: Int,
    message: String,
) {
    Icon(
        painter = painterResource(glyph),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_GLYPH_ALPHA),
        modifier = Modifier.size(EmptyStateGlyphSize).align(Alignment.CenterHorizontally),
    )
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
        color = homeMetaColor(),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = CardActionsGap, bottom = RepFlowSpacing.gapLg),
    )
}

/**
 * `4a`'s `Last workout` card (`:810-816`) - full width, since `This week`
 * beside it is not drawn (D9) - or `1d`'s history error card with `Retry`
 * (`:3010-3018`). Nothing is drawn before the first workout.
 */
@Composable
internal fun LastWorkoutCard(
    lastWorkout: HomeLastWorkout,
    today: LocalDate,
    onRetry: () -> Unit,
) {
    when (lastWorkout) {
        is HomeLastWorkout.Summary -> LastWorkoutSummaryCard(lastWorkout, today)
        HomeLastWorkout.Failed -> HistoryErrorCard(onRetry)
        HomeLastWorkout.Loading, HomeLastWorkout.None -> Unit
    }
}

@Composable
private fun LastWorkoutSummaryCard(
    summary: HomeLastWorkout.Summary,
    today: LocalDate,
) {
    val scheme = MaterialTheme.colorScheme
    val name = summary.planName ?: stringResource(R.string.home_untitled_workout)
    val meta =
        stringResource(
            R.string.home_last_workout_meta,
            workoutDayLabel(workoutDayOf(summary.endedAt, today)),
            durationMinutes(summary.startedAt, summary.endedAt).toInt(),
        )
    RepFlowCard(modifier = Modifier.fillMaxWidth(), contentPadding = CardPadding) {
        Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
            RepFlowSectionLabel(
                text = stringResource(R.string.home_last_workout_label),
                modifier = Modifier.padding(bottom = CardActionsGap),
            )
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = homeMetaColor(),
                modifier = Modifier.padding(top = 4.dp),
            )
            if (summary.loadIncreases > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_load_increases, summary.loadIncreases, summary.loadIncreases),
                    style = MaterialTheme.typography.bodySmall,
                    color = repFlowAccentOutlineColors(scheme).label,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.home_no_load_increases),
                    style = MaterialTheme.typography.bodySmall,
                    color = homeMetaColor(),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun workoutDayLabel(day: WorkoutDay): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (day) {
        WorkoutDay.Today -> stringResource(R.string.home_last_workout_today)
        WorkoutDay.Yesterday -> stringResource(R.string.home_last_workout_yesterday)
        is WorkoutDay.Weekday -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
        is WorkoutDay.OnDate -> day.date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
}

@Composable
private fun HistoryErrorCard(onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    RepFlowCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd)) {
            Icon(
                painter = painterResource(RepFlowIcons.warningCircle),
                contentDescription = null,
                tint = scheme.error,
                modifier = Modifier.size(19.dp).padding(top = 1.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_history_error_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                )
                Text(
                    text = stringResource(R.string.home_history_error_message),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = homeMetaColor(),
                    modifier = Modifier.padding(top = 2.dp),
                )
                RepFlowAccentOutlineButton(
                    text = stringResource(R.string.home_history_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = RepFlowSpacing.gapLg),
                )
            }
        }
    }
}
