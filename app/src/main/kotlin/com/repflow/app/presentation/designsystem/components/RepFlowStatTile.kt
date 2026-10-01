package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor

/*
 * The figure tiles: `3b`'s `Time · Volume · Sets` triple
 * (`RepFlow.dc.html:980-983`) and `4a`'s finish-screen summary. Radius 12,
 * `surface` fill, hairline ring, padding 12; an 11/500 uppercase caption over
 * a 19/500 tabular figure 4 below it; tiles 10 apart.
 */

object RepFlowStatTileDefaults {
    val shape = RoundedCornerShape(12.dp)
    val padding = 12.dp
    val borderWidth = 1.dp
    val valueFontSize = 19.sp
    val valueLineHeight = 24.sp
    val captionGap = 4.dp
    val tileGap = 10.dp
}

/** One tile's caption and figure, already formatted by the caller. */
@Immutable
data class RepFlowStat(
    val label: String,
    val value: String,
)

/**
 * A single stat tile. Read as one unit by accessibility services ("Volume,
 * 4 320 kg"), since a figure without its caption means nothing.
 */
@Composable
fun RepFlowStatTile(
    stat: RepFlowStat,
    modifier: Modifier = Modifier,
) {
    val shape = RepFlowStatTileDefaults.shape
    Column(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .background(color = MaterialTheme.colorScheme.surface, shape = shape)
                .border(BorderStroke(RepFlowStatTileDefaults.borderWidth, RepFlowColor.hairline), shape)
                .padding(RepFlowStatTileDefaults.padding),
        verticalArrangement = Arrangement.spacedBy(RepFlowStatTileDefaults.captionGap),
    ) {
        // The section label's look without its heading role: a caption, not a section.
        Text(
            text = stat.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stat.value,
            style =
                RepFlowNumericTextStyle.copy(
                    fontSize = RepFlowStatTileDefaults.valueFontSize,
                    lineHeight = RepFlowStatTileDefaults.valueLineHeight,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tiles side by side at equal width - the design's `repeat(3,1fr)` grid. */
@Composable
fun RepFlowStatRow(
    stats: List<RepFlowStat>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(RepFlowStatTileDefaults.tileGap),
    ) {
        stats.forEach { stat -> RepFlowStatTile(stat = stat, modifier = Modifier.weight(1f)) }
    }
}
