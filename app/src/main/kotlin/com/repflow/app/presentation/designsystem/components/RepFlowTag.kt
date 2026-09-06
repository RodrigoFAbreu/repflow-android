package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.isDarkColorScheme

/*
 * Pills, in both of the forms the design uses them: the static status chip,
 * and the horizontal row of selectable pills the 0-5 recovery scale, the RPE
 * picker, the pain picker and the technique picker are all instances of. They
 * live in one file because they are one pattern - four bespoke pickers is
 * exactly what this primitive exists to prevent.
 *
 * Neither reads `secondaryContainer`/`onSecondaryContainer`: those are scoped
 * to the bottom nav's own per-item override, so nothing here re-tints the
 * stock FilterChips on screens this milestone does not touch.
 */

/** The status-chip states the design draws. */
enum class RepFlowTagTone {
    /** Accent-tinted: a finished set, a met target. */
    Done,

    /** Neutral fill: queued, not yet reached. */
    Pending,

    /** Hairline border only, no fill. */
    Outline,

    /** The all-caps micro-pill. Callers pass the string already uppercased. */
    UpNext,
}

object RepFlowTagDefaults {
    val minHeight = 28.dp
    val horizontalPadding = 10.dp
    val borderWidth = 1.dp
    val iconSize = 14.dp
}

/** Sizing of one selectable pill row; see [RepFlowPillPickerDefaults]. */
@Immutable
data class RepFlowPillPickerSizing(
    val minHeight: Dp,
    val shape: Shape,
)

object RepFlowPillPickerDefaults {
    /**
     * Full-pill cells: the RPE, pain and technique rows.
     *
     * The design renders these 42dp tall, but its own layout rules also state
     * "every tap target is at least 44" - the two disagree by 2dp, and the
     * rule that protects the user wins.
     */
    val pill = RepFlowPillPickerSizing(minHeight = 44.dp, shape = RepFlowShapes.pill)

    /** Fixed cells at the control radius: the 0-5 recovery scale. */
    val scale = RepFlowPillPickerSizing(minHeight = 44.dp, shape = RoundedCornerShape(8.dp))
}

/**
 * The accent-tinted treatment shared by a selected pill and a "done" chip.
 *
 * Dark is the design's own value: `primary` at 22% over whatever sits behind,
 * an accent-600 border and an accent-300 label. Light has no scale-row or
 * status-chip render anywhere in the design to read a value from, so it reuses
 * the *confirmed* light accent-outline pair with no fill - a disclosed
 * judgment call, not a design-derived fact (see the milestone plan's "Known
 * limitations").
 */
@Immutable
internal data class RepFlowSelectedPillColors(
    val fill: Color,
    val border: Color,
    val label: Color,
)

internal fun repFlowSelectedPillColors(scheme: ColorScheme): RepFlowSelectedPillColors =
    if (isDarkColorScheme(scheme)) {
        RepFlowSelectedPillColors(
            fill = scheme.primary.copy(alpha = SELECTED_FILL_ALPHA_DARK),
            border = RepFlowColor.accent600,
            label = RepFlowColor.accent300,
        )
    } else {
        val accent = repFlowAccentOutlineColors(scheme)
        RepFlowSelectedPillColors(fill = Color.Transparent, border = accent.border, label = accent.label)
    }

/** The design's own `rgba(145,132,217,.22)`, expressed against `primary`. */
internal const val SELECTED_FILL_ALPHA_DARK = 0.22f

/**
 * A status chip: always a word, optionally a glyph too - the design's rule is
 * that state is never signalled by colour alone, which taking the label as a
 * required parameter enforces at the type level.
 */
@Composable
fun RepFlowStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: RepFlowTagTone = RepFlowTagTone.Outline,
    @DrawableRes icon: Int? = null,
) {
    val selected = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill =
        when (tone) {
            RepFlowTagTone.Done -> selected.fill
            RepFlowTagTone.Pending -> RepFlowColor.control
            RepFlowTagTone.Outline, RepFlowTagTone.UpNext -> Color.Transparent
        }
    val border =
        when (tone) {
            RepFlowTagTone.Done -> selected.border
            RepFlowTagTone.Pending -> Color.Transparent
            RepFlowTagTone.Outline, RepFlowTagTone.UpNext -> RepFlowColor.hairline
        }
    val label =
        when (tone) {
            RepFlowTagTone.Done -> selected.label
            else -> MaterialTheme.colorScheme.onSurface
        }
    val textStyle =
        when (tone) {
            RepFlowTagTone.UpNext -> MaterialTheme.typography.labelSmall
            else -> MaterialTheme.typography.labelMedium
        }

    Row(
        modifier =
            modifier
                .heightIn(min = RepFlowTagDefaults.minHeight)
                .clip(RepFlowShapes.pill)
                .background(color = fill, shape = RepFlowShapes.pill)
                .border(BorderStroke(RepFlowTagDefaults.borderWidth, border), RepFlowShapes.pill)
                .padding(horizontal = RepFlowTagDefaults.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapXs),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = label,
                modifier = Modifier.size(RepFlowTagDefaults.iconSize),
            )
        }
        // A chip is one short word or one short number pair. Constraining it to
        // a single ellipsized line means a longer string - a future locale, or
        // a rep-range/rest pair on a narrow screen - degrades visibly instead
        // of being clipped silently by the row that holds it.
        Text(
            text = text,
            style = textStyle,
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A horizontal row of selectable pills: one primitive for the 0-5 recovery
 * scale and for the RPE/pain/technique pickers, which are the same control.
 *
 * @param selectedIndex index into [options], or null while nothing is chosen.
 */
@Composable
fun RepFlowPillPicker(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    sizing: RepFlowPillPickerSizing = RepFlowPillPickerDefaults.pill,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapSm),
    ) {
        options.forEachIndexed { index, label ->
            PillPickerCell(
                label = label,
                selected = index == selectedIndex,
                sizing = sizing,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RowScope.PillPickerCell(
    label: String,
    selected: Boolean,
    sizing: RepFlowPillPickerSizing,
    onClick: () -> Unit,
) {
    val selectedColors = repFlowSelectedPillColors(MaterialTheme.colorScheme)
    val fill = if (selected) selectedColors.fill else Color.Transparent
    val border = if (selected) selectedColors.border else RepFlowColor.hairline
    val labelColor = if (selected) selectedColors.label else MaterialTheme.colorScheme.onSurface

    Box(
        modifier =
            Modifier
                .weight(1f)
                .defaultMinSize(minHeight = sizing.minHeight)
                .clip(sizing.shape)
                .background(color = fill, shape = sizing.shape)
                .border(BorderStroke(RepFlowTagDefaults.borderWidth, border), sizing.shape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
        )
    }
}
