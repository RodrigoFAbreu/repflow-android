package com.repflow.app.presentation.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.repflow.app.R
import com.repflow.app.application.history.BestSet
import com.repflow.app.application.history.PersonalBest
import com.repflow.app.domain.exercise.ExerciseId
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowCard
import com.repflow.app.presentation.designsystem.components.RepFlowCardTone
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowStat
import com.repflow.app.presentation.designsystem.components.RepFlowStatRow
import com.repflow.app.presentation.designsystem.components.RepFlowStepperMath
import com.repflow.app.presentation.designsystem.components.repFlowAccentOutlineColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import com.repflow.app.presentation.home.elapsedLabel
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Stateful route for the done screen (remediation-1 CP9): owns the ViewModel,
 * re-reads the recommendations on every `ON_START`, and hands navigation out.
 */
@Composable
fun WorkoutDoneRoute(
    onOpenRecommendation: (ExerciseId) -> Unit,
    onBackToHome: () -> Unit,
    viewModel: WorkoutDoneViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onRefreshRecommendations() }
    WorkoutDoneScreen(uiState = uiState, onOpenRecommendation = onOpenRecommendation, onBackToHome = onBackToHome)
}

/**
 * The done screen (`4a` `nDone`, `RepFlow.dc.html:1364-1405`): when it
 * finished, the workout's title, `Time` / `Sets` / `Trained`, a card per best
 * set (`D68`), `Versus last time` with one recap row per exercise (`D67`), the
 * progression recommendations this completion computed (`D37`), when it was
 * saved to History, and a pinned `Back to Home`.
 *
 * Workout mode is over, so there is no bottom nav; `Back to Home` is the one
 * primary action. The design's session note is not built (`D3`).
 */
@Composable
fun WorkoutDoneScreen(
    uiState: WorkoutDoneUiState,
    onOpenRecommendation: (ExerciseId) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val content = uiState.content) {
                WorkoutDoneContent.Loading -> RepFlowLoadingIndicator()
                WorkoutDoneContent.NotFound -> RepFlowEmptyState(message = stringResource(R.string.workout_done_not_found))
                WorkoutDoneContent.Failed -> RepFlowEmptyState(message = stringResource(R.string.workout_done_failed))
                is WorkoutDoneContent.Loaded -> DoneContent(content = content, onOpenRecommendation = onOpenRecommendation)
            }
        }
        RepFlowBottomActionBar(
            primaryText = stringResource(R.string.workout_done_back_home),
            onPrimaryClick = onBackToHome,
        )
    }
}

@Composable
private fun DoneContent(
    content: WorkoutDoneContent.Loaded,
    onOpenRecommendation: (ExerciseId) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = RepFlowSpacing.screenPadding,
                end = RepFlowSpacing.screenPadding,
                top = 24.dp,
                bottom = 8.dp,
            ),
    ) {
        item(key = "header") {
            Text(
                text =
                    stringResource(
                        R.string.workout_done_label,
                        DateTimeFormatter.ofPattern(DAY_PATTERN, locale).format(content.endedAt.atZone(zone)),
                    ).uppercase(locale),
                style = MaterialTheme.typography.labelSmall,
                color = secondary,
            )
            Text(
                text = content.planName ?: stringResource(R.string.home_untitled_workout),
                style =
                    MaterialTheme.typography.headlineSmall.copy(
                        fontSize = TitleFontSize,
                        lineHeight = TitleLineHeight,
                        fontWeight = FontWeight.Medium,
                    ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp).semantics { heading() },
            )
            RepFlowStatRow(
                stats =
                    listOf(
                        RepFlowStat(
                            label = stringResource(R.string.workout_done_stat_time),
                            value = elapsedLabel(Duration.between(content.startedAt, content.endedAt).seconds),
                        ),
                        RepFlowStat(label = stringResource(R.string.workout_done_stat_sets), value = content.workingSets.toString()),
                        RepFlowStat(
                            label = stringResource(R.string.workout_done_stat_trained),
                            value = stringResource(R.string.workout_done_trained_value, content.exercisesTrained, content.exerciseCount),
                        ),
                    ),
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
        items(items = content.personalBests, key = { "best-${it.exerciseId.value}" }) { best ->
            BestSetCard(best = best, locale = locale, zone = zone)
        }
        item(key = "versus") {
            RepFlowSectionLabel(
                text = stringResource(R.string.workout_done_versus_label),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        items(items = content.recaps, key = { "recap-${it.id.value}" }) { row -> RecapRow(row) }
        if (content.recommendations.isNotEmpty()) {
            item(key = "suggestions") {
                RepFlowSectionLabel(
                    text = stringResource(R.string.workout_done_recommendations_label),
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
                )
            }
            items(items = content.recommendations, key = { "suggestion-${it.exerciseId.value}" }) { item ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = RepFlowSpacing.gapSm)) {
                    Text(
                        text = item.exerciseName,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = RecapNameFontSize),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    RecommendationRow(
                        exerciseName = item.exerciseName,
                        recommendation = item.recommendation,
                        onWhyClick = { onOpenRecommendation(item.exerciseId) },
                    )
                }
            }
        }
        item(key = "saved") {
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    painter = painterResource(RepFlowIcons.clockCounterClockwise),
                    contentDescription = null,
                    tint = secondary,
                    modifier = Modifier.size(SavedIconSize),
                )
                Text(
                    text =
                        stringResource(
                            R.string.workout_done_saved_line,
                            time.format(content.startedAt.atZone(zone)),
                            time.format(content.endedAt.atZone(zone)),
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondary,
                )
            }
        }
    }
}

/** `Best set on <exercise>` and what it beat, on the accent card (`ph-fill ph-medal`). */
@Composable
private fun BestSetCard(
    best: PersonalBest,
    locale: Locale,
    zone: ZoneId,
) {
    RepFlowCard(
        tone = RepFlowCardTone.Accent,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                painter = painterResource(RepFlowIcons.medalFill),
                contentDescription = null,
                tint = RepFlowColor.accent300,
                modifier = Modifier.size(MedalIconSize),
            )
            Column {
                Text(
                    text = stringResource(R.string.workout_done_best_set_title, best.exerciseName),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = BestSetFontSize, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text =
                        stringResource(
                            R.string.workout_done_best_set_detail,
                            bestSetText(best.best),
                            bestSetText(best.previous),
                            DateTimeFormatter.ofPattern(SHORT_DATE_PATTERN, locale).format(best.previousEndedAt.atZone(zone)),
                        ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = BestSetFontSize, fontFeatureSettings = "tnum"),
                    color = LocalContentColor.current.copy(alpha = BEST_SET_DETAIL_ALPHA),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** One `Versus last time` row: name, what was logged, and the delta - dimmed with `—` when nothing was. */
@Composable
private fun RecapRow(row: RecapRowUi) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val trained = row.workingSets > 0
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = RecapNameFontSize),
                    color = if (trained) MaterialTheme.colorScheme.onBackground else secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = recapDetail(row),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = secondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = deltaText(row.delta),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = DeltaFontSize, fontFeatureSettings = "tnum"),
                color = if (row.delta.isGain()) repFlowAccentOutlineColors(MaterialTheme.colorScheme).label else secondary,
                maxLines = 1,
            )
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha),
        )
    }
}

/** `Tuesday, 11 Aug`. */
private const val DAY_PATTERN = "EEEE, d MMM"

/** `5 Aug`. */
private const val SHORT_DATE_PATTERN = "d MMM"

private const val BEST_SET_DETAIL_ALPHA = 0.72f

/** `30/500`. */
private val TitleFontSize = 30.sp
private val TitleLineHeight = 34.sp
private val BestSetFontSize = 13.5.sp
private val MedalIconSize = 20.dp
private val RecapNameFontSize = 14.5.sp
private val DeltaFontSize = 13.sp
private val SavedIconSize = 15.dp
