package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.isDarkColorScheme

/*
 * The three button tiers, from RepFlow's own component spec.
 *
 * Primary is a solid accent fill; the two outline tiers differ by more than
 * their border colour, so they are separate composables rather than one
 * parameterised button - the accent tier is the design's own "secondary
 * action" (48dp, accent ramp, 14.5sp) and the neutral tier its quietest one
 * (44dp, hairline, 80%-opacity label).
 *
 * The outline tiers draw RepFlowColor.hairline / the accent ramp explicitly
 * rather than ColorScheme.outline: that role is also every stock
 * OutlinedTextField's and Switch's resting border, where the design's hairline
 * value measures 1.2-1.8:1 (see ROLE_AUDIT.md).
 */

/**
 * The dimensions and label typography of the three tiers.
 *
 * Kept as plain values rather than `@Composable` getters so they are readable
 * from a plain-JVM test - the sizes are as much a transcription of the design
 * as the colour hexes are, and nothing else would catch a typo in one.
 */
object RepFlowButtonDefaults {
    /** Primary: 56dp tall, 12dp radius - `medium` in the shape scheme. */
    val primaryMinHeight = 56.dp
    val primaryShape = RoundedCornerShape(12.dp)
    val primaryFontSize = 16.sp

    /** The design's single confirmed weight-600 usage; nothing else uses it. */
    val primaryFontWeight = FontWeight.SemiBold

    /** Accent outline: 48dp tall, 10dp radius - inside the 10-12dp button range. */
    val accentOutlineMinHeight = 48.dp
    val accentOutlineShape = RoundedCornerShape(10.dp)
    val accentOutlineFontSize = 14.5.sp

    /** Neutral outline: 44dp tall, 8dp radius - `small` in the shape scheme. */
    val neutralOutlineMinHeight = 44.dp
    val neutralOutlineShape = RoundedCornerShape(8.dp)
    val neutralOutlineFontSize = 14.sp
    val neutralOutlineLabelAlpha = 0.8f

    /** Every border this design system draws is a hairline. */
    val borderWidth = 1.dp

    val leadingIconSize = 18.dp
    val leadingIconGap = 8.dp
}

/**
 * The accent border/label pair, which is *not* the same ramp step in both
 * themes: dark reads accent-700/accent-300, light accent-600/accent-700.
 * Applying dark's pair to light would put `#d2cefd` text on `#f3f5fe` - 1.38:1,
 * effectively invisible.
 *
 * This is also the pair the primitives with no design-confirmed light-theme
 * render fall back to (see [repFlowSelectedPillColors] and `RepFlowTag.kt`).
 */
@Immutable
internal data class RepFlowAccentOutlineColors(
    val border: Color,
    val label: Color,
)

internal fun repFlowAccentOutlineColors(scheme: ColorScheme): RepFlowAccentOutlineColors =
    if (isDarkColorScheme(scheme)) {
        RepFlowAccentOutlineColors(border = RepFlowColor.accent700, label = RepFlowColor.accent300)
    } else {
        RepFlowAccentOutlineColors(border = RepFlowColor.accent600, label = RepFlowColor.accent700)
    }

/**
 * The one primary action on a screen: solid accent fill, `onPrimary` label at
 * the design's singular weight 600.
 */
@Composable
fun RepFlowPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
    minHeight: Dp = RepFlowButtonDefaults.primaryMinHeight,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = minHeight),
        enabled = enabled,
        shape = RepFlowButtonDefaults.primaryShape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        ButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = RepFlowButtonDefaults.primaryFontSize,
                    fontWeight = RepFlowButtonDefaults.primaryFontWeight,
                ),
        )
    }
}

/** The design's secondary action: accent-ramp border and label, no fill. */
@Composable
fun RepFlowAccentOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val accent = repFlowAccentOutlineColors(MaterialTheme.colorScheme)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = RepFlowButtonDefaults.accentOutlineMinHeight),
        enabled = enabled,
        shape = RepFlowButtonDefaults.accentOutlineShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent.label),
        border = BorderStroke(RepFlowButtonDefaults.borderWidth, accent.border),
    ) {
        ButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = RepFlowButtonDefaults.accentOutlineFontSize),
        )
    }
}

/** The quietest tier: hairline border, label at 80% of the text colour. */
@Composable
fun RepFlowNeutralOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = RepFlowButtonDefaults.neutralOutlineMinHeight),
        enabled = enabled,
        shape = RepFlowButtonDefaults.neutralOutlineShape,
        colors =
            ButtonDefaults.outlinedButtonColors(
                contentColor =
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = RepFlowButtonDefaults.neutralOutlineLabelAlpha,
                    ),
            ),
        border = BorderStroke(RepFlowButtonDefaults.borderWidth, RepFlowColor.hairline),
    ) {
        ButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = RepFlowButtonDefaults.neutralOutlineFontSize),
        )
    }
}

/**
 * Emits the label (and its optional leading glyph) straight into the button's
 * own `RowScope`, so the three tiers above share one arrangement.
 */
@Composable
private fun ButtonLabel(
    text: String,
    @DrawableRes leadingIcon: Int?,
    style: TextStyle,
) {
    if (leadingIcon != null) {
        Icon(
            painter = painterResource(leadingIcon),
            contentDescription = null,
            modifier = Modifier.size(RepFlowButtonDefaults.leadingIconSize),
        )
        Spacer(Modifier.width(RepFlowButtonDefaults.leadingIconGap))
    }
    Text(text = text, style = style)
}
