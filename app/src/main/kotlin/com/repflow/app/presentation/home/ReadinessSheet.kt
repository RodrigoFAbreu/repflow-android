package com.repflow.app.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.domain.recovery.ReadinessFactorReading
import com.repflow.app.domain.recovery.ReadinessScore
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.components.RepFlowBottomActionBar
import com.repflow.app.presentation.designsystem.components.RepFlowSectionLabel
import com.repflow.app.presentation.designsystem.components.RepFlowSheet
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The readiness detail sheet - `4a`'s `nRdySheet` (`RepFlow.dc.html:1489-1547`)
 * - built by remediation-1 CP4 and hosted by Home from CP5 (the score on
 * Home's recovery card opens it).
 *
 * Drawn top to bottom: title and one-line explanation; the score at 44/500
 * in the band colour beside the band word and "out of 100"; a 4dp bar at the
 * score; the driver sentence; `The inputs and what they weigh` with one row
 * per factor; the gate note; then `Close` on a pinned bar.
 *
 * Deliberately **not** drawn (deviation register): the band's advice line
 * (D19), and the `What it proposes, exercise by exercise` list with its
 * override button (D29) - nothing in the app acts on the band, so advice and
 * decisions it does not follow would be false. For the same reason the
 * title, the explanation and the gate note do not claim the score adjusts
 * anything (D42). Each factor row adds its `v/5` and its flagged/fine word to
 * the design's label, dots and weight (D43, plan CP4 item 4), so "pulling the
 * score down" is never carried by colour alone.
 */

private val ScoreTextStyle = RepFlowNumericTextStyle.copy(fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em)
private val BarHeight = 4.dp
private val BarShape = RoundedCornerShape(2.dp)
private val DotSize = 7.dp
private val DotGap = 3.dp
private val WeightColumnWidth = 34.dp
private val CloseMinHeight = 48.dp
private const val SCALE_DOTS = 5
private const val SCORE_MAX = 100f
private const val DRIVER_TEXT_ALPHA = 0.75f
private const val CLOSE_TEXT_ALPHA = 0.70f
private const val TRACK_ALPHA = 0.12f
private const val EMPTY_DOT_ALPHA = 0.14f
private const val ROW_DIVIDER_ALPHA = 0.08f

/** The sheet itself: [ReadinessDetail] inside the design's [RepFlowSheet]. */
@Composable
fun ReadinessSheet(
    readiness: ReadinessScore,
    onDismissRequest: () -> Unit,
) {
    RepFlowSheet(onDismissRequest = onDismissRequest) {
        ReadinessDetail(
            readiness = readiness,
            onClose = onDismissRequest,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * The sheet's content without the modal around it, so it can be shown and
 * tested on its own: a scrolling body and the pinned `Close` bar.
 */
@Composable
fun ReadinessDetail(
    readiness: ReadinessScore,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bandColor = readinessBandColor(readiness.band, scheme)
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = RepFlowSpacing.screenPadding,
                        end = RepFlowSpacing.screenPadding,
                        top = 4.dp,
                        bottom = RepFlowSpacing.screenPadding,
                    ),
        ) {
            Text(
                text = stringResource(R.string.readiness_sheet_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.readiness_sheet_intro),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(scheme),
                modifier = Modifier.padding(top = 4.dp, bottom = RepFlowSpacing.screenPadding),
            )
            ScoreHeadline(readiness = readiness, bandColor = bandColor)
            ScoreBar(score = readiness.score, bandColor = bandColor)
            Text(
                text = readiness.driverSentence,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
                color = scheme.onSurface.copy(alpha = DRIVER_TEXT_ALPHA),
            )
            RepFlowSectionLabel(
                text = stringResource(R.string.readiness_sheet_inputs_label),
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
            )
            readiness.factors.forEach { reading ->
                FactorRow(reading = reading, bandColor = bandColor)
            }
            Text(
                text = stringResource(R.string.readiness_sheet_gate_note),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = repFlowSecondaryTextColor(scheme),
                modifier = Modifier.padding(top = RepFlowSpacing.gapMd),
            )
        }
        RepFlowBottomActionBar {
            TextButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().heightIn(min = CloseMinHeight),
            ) {
                Text(
                    text = stringResource(R.string.readiness_sheet_close),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = scheme.onSurface.copy(alpha = CLOSE_TEXT_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun ScoreHeadline(
    readiness: ReadinessScore,
    bandColor: Color,
) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapLg),
    ) {
        Text(text = readiness.score.toString(), style = ScoreTextStyle, color = bandColor)
        Column(modifier = Modifier.weight(1f).padding(bottom = 4.dp)) {
            Text(
                text = stringResource(readinessBandLabel(readiness.band)),
                style = MaterialTheme.typography.titleMedium,
                color = bandColor,
            )
            Text(
                text = stringResource(R.string.readiness_sheet_out_of),
                style = MaterialTheme.typography.bodySmall,
                color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            )
        }
    }
}

/** The 4dp bar at the score's percentage. Decorative: the number above says the same. */
@Composable
private fun ScoreBar(
    score: Int,
    bandColor: Color,
) {
    Box(
        modifier =
            Modifier
                .padding(top = RepFlowSpacing.gapLg, bottom = RepFlowSpacing.gapMd)
                .fillMaxWidth()
                .height(BarHeight)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = TRACK_ALPHA), BarShape)
                .clearAndSetSemantics {},
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(score / SCORE_MAX)
                    .height(BarHeight)
                    .background(bandColor, BarShape),
        )
    }
}

/**
 * One input: its label (in the band colour when it is pulling the score
 * down), `v/5` and the flagged/fine word under it, five dots filled to the
 * normalized value, and its weight.
 */
@Composable
private fun FactorRow(
    reading: ReadinessFactorReading,
    bandColor: Color,
) {
    val scheme = MaterialTheme.colorScheme
    val secondary = repFlowSecondaryTextColor(scheme)
    val note =
        stringResource(
            if (reading.isFlagged) R.string.readiness_factor_flagged else R.string.readiness_factor_fine,
        )
    val weight = reading.factor.weightTenths
    Column(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reading.factor.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 19.sp),
                    color = if (reading.isFlagged) bandColor else secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.readiness_factor_reading, reading.value, note),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                    color = secondary,
                )
            }
            Dots(filled = reading.normalized, filledColor = if (reading.isFlagged) bandColor else scheme.primary)
            Text(
                text = stringResource(R.string.readiness_factor_weight, weight / WEIGHT_TENTHS, weight % WEIGHT_TENTHS),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"),
                color = secondary,
                modifier = Modifier.width(WeightColumnWidth),
                textAlign = TextAlign.End,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = scheme.onSurface.copy(alpha = ROW_DIVIDER_ALPHA))
    }
}

/** Five dots filled to the normalized value. Decorative: the `v/5` line carries the number. */
@Composable
private fun Dots(
    filled: Int,
    filledColor: Color,
) {
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = EMPTY_DOT_ALPHA)
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(DotGap),
    ) {
        repeat(SCALE_DOTS) { index ->
            Box(
                modifier =
                    Modifier
                        .size(DotSize)
                        .background(if (index < filled) filledColor else empty, CircleShape),
            )
        }
    }
}

private const val WEIGHT_TENTHS = 10
