package com.repflow.app.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.recovery.FutsalSession
import com.repflow.app.domain.recovery.RecoveryEntry
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowEmptyState
import com.repflow.app.presentation.designsystem.components.RepFlowLoadingIndicator
import com.repflow.app.presentation.designsystem.components.RepFlowScreenScaffold
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Read-only recovery/futsal history (Milestone 8, CP4), as `3d` draws it -
 * "trend first, entries second" (remediation-1 CP13): CP3's sub-screen bar,
 * the fourteen-day sleep and energy chart, then every check-in under
 * `Entries` and every session under `Futsal sessions`.
 */
@Composable
fun RecoveryHistoryScreen(
    uiState: RecoveryHistoryUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RepFlowScreenScaffold(
        title = stringResource(R.string.recovery_history_title),
        onBack = onBackClick,
        modifier = modifier,
    ) { padding ->
        when {
            uiState.isLoading -> {
                RepFlowLoadingIndicator(Modifier.padding(padding))
            }

            uiState.recoveryEntries.isEmpty() && uiState.futsalSessions.isEmpty() -> {
                RepFlowEmptyState(
                    message = stringResource(R.string.recovery_history_empty),
                    modifier = Modifier.padding(padding),
                    icon = RepFlowIcons.moonStars,
                )
            }

            else -> {
                RecoveryHistoryContent(uiState = uiState, today = uiState.today ?: LocalDate.now(), padding = padding)
            }
        }
    }
}

@Composable
private fun RecoveryHistoryContent(
    uiState: RecoveryHistoryUiState,
    today: LocalDate,
    padding: PaddingValues,
) {
    val trend =
        remember(uiState.recoveryEntries, uiState.futsalSessions, today) {
            recoveryTrendOf(uiState.recoveryEntries, uiState.futsalSessions, today)
        }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(top = ListTopPadding, bottom = ListBottomPadding),
    ) {
        item(key = "trend") { RecoveryTrendCard(trend = trend, today = today) }
        if (uiState.recoveryEntries.isNotEmpty()) {
            item(key = "entries-label") { SectionLabel(R.string.recovery_history_recovery_section_title) }
            items(uiState.recoveryEntries, key = { "recovery-${it.id.value}" }) { entry ->
                RecoveryEntryRow(entry, today)
            }
        }
        if (uiState.futsalSessions.isNotEmpty()) {
            item(key = "futsal-label") { SectionLabel(R.string.recovery_history_futsal_section_title) }
            items(uiState.futsalSessions, key = { "futsal-${it.id.value}" }) { session ->
                FutsalSessionRow(session, today)
            }
        }
    }
}

@Composable
private fun SectionLabel(textRes: Int) {
    RepFlowSectionLabel(
        text = stringResource(textRes),
        modifier = Modifier.padding(top = ListSectionTopGap, bottom = ListSectionBottomGap),
    )
}

/** `Today | Sleep 4 · Energy 3 · DOMS 2 | ⚽` (`:2164-2168`); the mark is the check-in's "Played in last 24h". */
@Composable
private fun RecoveryEntryRow(
    entry: RecoveryEntry,
    today: LocalDate,
) {
    HistoryRow(
        dateLabel = recoveryDayLabel(entry.date, today),
        text = stringResource(R.string.recovery_history_recovery_row, entry.sleepQuality, entry.energy, entry.legDoms),
    ) {
        if (entry.futsalInPrevious24h) {
            Icon(
                painter = painterResource(RepFlowIcons.soccerBall),
                contentDescription = stringResource(R.string.recovery_history_played_content_description),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(RowGlyphSize),
            )
        }
    }
}

/** `10 Aug | 50 min · RPE 8 | load 400` (`:2188-2192`). */
@Composable
private fun FutsalSessionRow(
    session: FutsalSession,
    today: LocalDate,
) {
    HistoryRow(
        dateLabel = recoveryDayLabel(session.date, today),
        text = stringResource(R.string.recovery_history_futsal_row, session.durationMinutes, plainNumber(session.sessionRpe)),
    ) {
        Text(
            text = stringResource(R.string.recovery_history_futsal_load, plainNumber(session.load)),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
    }
}

/** `3d`'s row: a 56-wide date at 12.5 in the meta tone, the values at 14 tabular, a trailing mark, a 9% divider. */
@Composable
private fun HistoryRow(
    dateLabel: String,
    text: String,
    trailing: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                modifier = Modifier.widthIn(min = DateColumnWidth),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha))
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val dayWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

/** `Today`, `10 Aug`, or `10 Aug 2025` for an earlier year. */
@Composable
internal fun recoveryDayLabel(
    date: LocalDate,
    today: LocalDate,
): String =
    when {
        date == today -> stringResource(R.string.recovery_history_today)
        date.year == today.year -> date.format(dayFormatter)
        else -> date.format(dayWithYearFormatter)
    }

/** `8`, `7.5`, `420` - never `8.0` or `420.0`. */
private fun plainNumber(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private val ListTopPadding = 4.dp
private val ListBottomPadding = 18.dp
private val ListSectionTopGap = 20.dp
private val ListSectionBottomGap = 4.dp
private val RowVerticalPadding = 13.dp
private val RowHorizontalPadding = 2.dp
private val DateColumnWidth = 56.dp
private val RowGlyphSize = 16.dp
