package com.repflow.app.presentation.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.application.progress.ExerciseRecords
import com.repflow.app.application.progress.WeeklyFrequency
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowStat
import com.repflow.app.presentation.designsystem.components.RepFlowStatRow
import com.repflow.app.presentation.designsystem.components.repFlowSelectedPillColors
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/*
 * `5b`'s lower sections, all for the chosen exercise (Q6): the `Sessions` and
 * `Avg RPE` tiles, `Training frequency` (eight weekly bars, the last in the
 * accent, and the average) and `Records`, each dated. Sessions and Avg RPE follow the range pill;
 * frequency and records do not.
 */

@Composable
internal fun ProgressStatsSection(
    stats: ProgressStats,
    unit: String,
    now: Instant,
    zone: ZoneId,
) {
    val locale = currentProgressLocale()
    RepFlowStatRow(
        stats =
            listOf(
                RepFlowStat(
                    stringResource(R.string.progress_tile_sessions),
                    stats.sessions.toString(),
                    note = stringResource(R.string.progress_tile_sessions_note),
                ),
                RepFlowStat(
                    stringResource(R.string.progress_tile_avg_rpe),
                    stats.averageRpe?.let { plainNumber(it, locale) } ?: stringResource(R.string.progress_none),
                    note = stringResource(R.string.progress_tile_avg_rpe_note),
                ),
            ),
        modifier = Modifier.padding(top = SectionTopGap),
    )
    RepFlowSectionLabel(
        text = stringResource(R.string.progress_frequency_title),
        modifier = Modifier.padding(top = LabelTopGap, bottom = LabelBottomGap),
    )
    FrequencyCard(frequency = stats.frequency)
    stats.records?.let { records ->
        RepFlowSectionLabel(
            text = stringResource(R.string.progress_records_title),
            modifier = Modifier.padding(top = LabelTopGap, bottom = RecordsLabelBottomGap),
        )
        RecordRows(records = records, unit = unit, nowYear = now.atZone(zone).year, zone = zone)
    }
}

@Composable
private fun FrequencyCard(frequency: WeeklyFrequency) {
    val secondary = repFlowSecondaryTextColor(MaterialTheme.colorScheme)
    val locale = currentProgressLocale()
    val counts = frequency.weeks.map { it.sessions }
    val description = stringResource(R.string.progress_frequency_description, counts.joinToString(", "))
    val shape = RoundedCornerShape(CardRadius)
    val older = RepFlowColor.accent700
    val current = MaterialTheme.colorScheme.primary
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.dp, RepFlowColor.hairline), shape)
                .padding(CardPadding),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(BarsHeight).semantics { contentDescription = description }) {
            val gap = BarGap.toPx()
            val barWidth = (size.width - gap * (counts.size - 1)) / counts.size
            val tallest = counts.max().coerceAtLeast(1)
            val radius = CornerRadius(BarRadius.toPx())
            counts.forEachIndexed { index, sessions ->
                val barHeight = maxOf(size.height * sessions / tallest, MinBarHeight.toPx())
                val path =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                rect = Rect(Offset(index * (barWidth + gap), size.height - barHeight), Size(barWidth, barHeight)),
                                topLeft = radius,
                                topRight = radius,
                                bottomRight = CornerRadius.Zero,
                                bottomLeft = CornerRadius.Zero,
                            ),
                        )
                    }
                drawPath(path, color = if (index == counts.lastIndex) current else older)
            }
        }
        // `8b`: no axis labels - the caption says the window (Monday to Sunday, the last 8 weeks) and the average.
        Text(
            text = stringResource(R.string.progress_frequency_caption, plainNumber(frequency.averagePerWeek, locale)),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = AverageFontSize),
            color = secondary,
            modifier = Modifier.padding(top = RepFlowSpacing.gapSm),
        )
    }
}

@Composable
private fun RecordRows(
    records: ExerciseRecords,
    unit: String,
    nowYear: Int,
    zone: ZoneId,
) {
    val locale = currentProgressLocale()

    fun date(at: Instant) = dateLabel(at, zone, locale, withYear = at.atZone(zone).year != nowYear)

    @Composable
    fun withUnit(value: BigDecimal) = stringResource(R.string.progress_value_with_unit, plainNumber(value, locale), unit)
    when (records) {
        is ExerciseRecords.Loaded -> {
            RecordRow(
                icon = RepFlowIcons.medalFill,
                accent = true,
                title =
                    stringResource(
                        R.string.progress_record_heaviest,
                        withUnit(records.heaviestSet.load),
                        records.heaviestSet.reps,
                    ),
                date = date(records.heaviestSet.on),
            )
            val estimate = records.bestEstimatedOneRepMax
            RecordRow(
                icon = RepFlowIcons.trendUp,
                accent = false,
                title =
                    if (estimate == null) {
                        stringResource(R.string.progress_record_estimate_none)
                    } else {
                        stringResource(R.string.progress_record_estimate, withUnit(estimate.value))
                    },
                date = estimate?.let { date(it.on) },
            )
        }

        is ExerciseRecords.MostReps -> {
            RecordRow(
                icon = RepFlowIcons.medalFill,
                accent = true,
                title = stringResource(R.string.progress_record_most_reps, records.reps),
                date = date(records.on),
            )
        }

        is ExerciseRecords.LongestHold -> {
            RecordRow(
                icon = RepFlowIcons.medalFill,
                accent = true,
                title = stringResource(R.string.progress_record_longest_hold, withUnit(BigDecimal(records.seconds))),
                date = date(records.on),
            )
        }
    }
}

@Composable
private fun RecordRow(
    icon: Int,
    accent: Boolean,
    title: String,
    date: String?,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint =
                    if (accent) {
                        repFlowSelectedPillColors(MaterialTheme.colorScheme).label
                    } else {
                        repFlowSecondaryTextColor(MaterialTheme.colorScheme)
                    },
                modifier = Modifier.size(IconSize),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = RecordFontSize, fontFeatureSettings = "tnum"))
                if (date != null) {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = DateFontSize),
                        color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.dividerAlpha))
    }
}

private val SectionTopGap = 14.dp
private val LabelTopGap = 20.dp
private val LabelBottomGap = 6.dp
private val RecordsLabelBottomGap = 4.dp
private val CardRadius = 12.dp
private val CardPadding = 14.dp
private val BarsHeight = 46.dp
private val BarGap = 4.dp
private val BarRadius = 3.dp
private val MinBarHeight = 3.dp
private val AverageFontSize = 12.5.sp
private val RowHorizontalPadding = 2.dp
private val RowVerticalPadding = 12.dp
private val RowGap = 12.dp
private val IconSize = 20.dp
private val RecordFontSize = 14.5.sp
private val DateFontSize = 12.5.sp
