package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowDarkColorScheme
import com.repflow.app.presentation.designsystem.RepFlowSpacing

/*
 * The card primitive: a hairline inset border over a flat fill, never a
 * lighter fill alone and never a drop shadow - "elevation is a hairline plus
 * ambient shadow" is one of the design's own layout rules.
 *
 * Fill and border are applied literally (Modifier.background/border), so this
 * card never performs Material 3's own container lookup. That matters: stock
 * `Card` reads `surfaceContainerHighest`, a role this milestone assigns for
 * the app's one bare `Card(` call site and which is deliberately *not* this
 * card's fill.
 */

/** Which of the design's two card treatments to draw. */
enum class RepFlowCardTone {
    /** The default: page-surface fill, hairline border. */
    Default,

    /**
     * The accent-tinted variant used by recommendation/suggestion surfaces.
     *
     * Its fill and border are theme-independent design constants, so this card
     * is a dark surface in both themes and carries its own light content
     * colour rather than the light theme's near-black `onSurface`, which would
     * measure about 1:1 on it. The design renders this variant in dark theme
     * only, so light theme's treatment of it is an undecided judgment call the
     * same way the light selected pill is (see `RepFlowTag.kt`).
     */
    Accent,
}

object RepFlowCardDefaults {
    /**
     * 16dp - the top of the design's confirmed 14-16dp card range, and the
     * shape scheme's own `large`, so cards and sheets share one radius rather
     * than introducing a fourth one-off value.
     */
    val shape = RoundedCornerShape(16.dp)

    val borderWidth = 1.dp

    /**
     * The accent card's content colour: the dark scheme's `onSurface`, because
     * the accent fill is a dark surface whichever theme is in force.
     */
    val accentContentColor: Color = RepFlowDarkColorScheme.onSurface
}

/**
 * A card: hairline border, flat fill, content laid out in a column.
 *
 * @param onClick when non-null the whole card is the tap target, as the design
 *   draws it for list rows; when null the card is inert decoration.
 */
@Composable
fun RepFlowCard(
    modifier: Modifier = Modifier,
    tone: RepFlowCardTone = RepFlowCardTone.Default,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(RepFlowSpacing.cardPaddingMin),
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill =
        when (tone) {
            RepFlowCardTone.Default -> MaterialTheme.colorScheme.surface
            RepFlowCardTone.Accent -> RepFlowColor.accent900
        }
    val border =
        when (tone) {
            RepFlowCardTone.Default -> RepFlowColor.hairline
            RepFlowCardTone.Accent -> RepFlowColor.accent700
        }
    val contentColor =
        when (tone) {
            RepFlowCardTone.Default -> MaterialTheme.colorScheme.onSurface
            RepFlowCardTone.Accent -> RepFlowCardDefaults.accentContentColor
        }
    val shape = RepFlowCardDefaults.shape

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier =
                modifier
                    .clip(shape)
                    .background(color = fill, shape = shape)
                    .border(BorderStroke(RepFlowCardDefaults.borderWidth, border), shape)
                    .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
                    .padding(contentPadding),
            content = content,
        )
    }
}
