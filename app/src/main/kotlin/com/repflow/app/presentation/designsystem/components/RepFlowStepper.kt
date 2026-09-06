package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons

/*
 * The minus / value / plus stepper the weight and reps entry uses.
 *
 * The design draws it at two sizes - the in-context set-entry pad's 44dp
 * buttons with a 24sp value, and the spec sheet's larger 48dp/32sp demo - so
 * the sizing is a parameter with the in-context pair as the default, rather
 * than one hardcoded size and a second copy of the component later.
 *
 * The value renders in the tabular numeric style, so a digit changing under
 * the user's thumb does not reflow the row.
 */

/** One of the design's two stepper sizes; see [RepFlowStepperDefaults]. */
@Immutable
data class RepFlowStepperSizing(
    val buttonSize: Dp,
    val buttonShape: Shape,
    val valueFontSize: TextUnit,
    val valueLineHeight: TextUnit,
)

object RepFlowStepperDefaults {
    /** The set-entry pad's own sizing, and the default: 44dp buttons, 24sp value. */
    val compact =
        RepFlowStepperSizing(
            buttonSize = 44.dp,
            buttonShape = RepFlowShapes.stepper,
            valueFontSize = 24.sp,
            valueLineHeight = 30.sp,
        )

    /** The spec sheet's larger demo: 48dp buttons, 32sp value. */
    val large =
        RepFlowStepperSizing(
            buttonSize = 48.dp,
            buttonShape = RoundedCornerShape(10.dp),
            valueFontSize = 32.sp,
            valueLineHeight = 38.sp,
        )

    val borderWidth = 1.dp
    val iconSize = 20.dp
}

/**
 * A stepper. The value is rendered, not owned: the caller keeps the number and
 * decides what a step does, so nothing about rounding or bounds lives here.
 *
 * @param decrementContentDescription spoken label for the minus button; the
 *   glyph itself carries no text, so this is the only thing a screen reader
 *   has to announce.
 */
@Composable
fun RepFlowStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    decrementContentDescription: String,
    incrementContentDescription: String,
    modifier: Modifier = Modifier,
    sizing: RepFlowStepperSizing = RepFlowStepperDefaults.compact,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowSpacing.gapMd),
    ) {
        StepperButton(
            icon = RepFlowIcons.minus,
            contentDescription = decrementContentDescription,
            sizing = sizing,
            onClick = onDecrement,
        )
        Text(
            text = value,
            style =
                RepFlowNumericTextStyle.copy(
                    fontSize = sizing.valueFontSize,
                    lineHeight = sizing.valueLineHeight,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = RepFlowIcons.plus,
            contentDescription = incrementContentDescription,
            sizing = sizing,
            onClick = onIncrement,
        )
    }
}

@Composable
private fun StepperButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    sizing: RepFlowStepperSizing,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(sizing.buttonSize)
                .clip(sizing.buttonShape)
                .background(color = RepFlowColor.control, shape = sizing.buttonShape)
                .border(
                    BorderStroke(RepFlowStepperDefaults.borderWidth, RepFlowColor.hairline),
                    sizing.buttonShape,
                ).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(RepFlowStepperDefaults.iconSize),
        )
    }
}
