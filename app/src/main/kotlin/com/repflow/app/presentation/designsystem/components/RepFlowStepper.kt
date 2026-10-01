package com.repflow.app.presentation.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.RepFlowShapes
import com.repflow.app.presentation.designsystem.RepFlowSpacing
import com.repflow.app.presentation.designsystem.icons.RepFlowIcons
import java.math.BigDecimal

/*
 * The minus / value / plus stepper - `6b`'s "the numeric input everywhere".
 *
 * The design draws it at two sizes - the in-context set-entry pad's 44dp
 * buttons with a 24sp value, and the spec sheet's larger 48dp/32sp demo - so
 * the sizing is a parameter with the in-context pair as the default, rather
 * than one hardcoded size and a second copy of the component later.
 *
 * `6b` also makes the value itself a button: "The value itself is a button -
 * it opens the keypad. Step size comes from the exercise's load increment."
 * Remediation-1 CP3 adds both halves. The stateless overload takes an
 * optional value-tap callback and draws the value as a ringed button when it
 * is given; the [BigDecimal] overload owns the arithmetic (a step size and a
 * range, via [RepFlowStepperMath]) and opens [RepFlowNumericKeypad] itself.
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

    /** `6b`: `gap:6px` between the three cells. */
    val gap = 6.dp

    /** `6b`: the value button's own `border-radius:10px`. */
    val valueShape = RoundedCornerShape(10.dp)
}

/**
 * A stepper. The value is rendered, not owned: the caller keeps the number and
 * decides what a step does, so nothing about rounding or bounds lives here.
 *
 * @param decrementContentDescription spoken label for the minus button; the
 *   glyph itself carries no text, so this is the only thing a screen reader
 *   has to announce.
 * @param onValueClick when non-null the value is a button (`6b`) - normally
 *   it opens [RepFlowNumericKeypad]; when null the value is plain text.
 * @param valueClickLabel what activating the value does, for accessibility
 *   services.
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
    onValueClick: (() -> Unit)? = null,
    valueClickLabel: String = stringResource(R.string.repflow_stepper_type_value),
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RepFlowStepperDefaults.gap),
    ) {
        StepperButton(
            icon = RepFlowIcons.minus,
            contentDescription = decrementContentDescription,
            sizing = sizing,
            onClick = onDecrement,
        )
        val valueModifier =
            if (onValueClick == null) {
                Modifier.weight(1f)
            } else {
                val shape = RepFlowStepperDefaults.valueShape
                Modifier
                    .weight(1f)
                    .heightIn(min = sizing.buttonSize)
                    .clip(shape)
                    .border(
                        BorderStroke(
                            RepFlowStepperDefaults.borderWidth,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = RepFlowColor.stepperValueRingAlpha),
                        ),
                        shape,
                    ).clickable(role = Role.Button, onClickLabel = valueClickLabel, onClick = onValueClick)
            }
        Box(modifier = valueModifier, contentAlignment = Alignment.Center) {
            Text(
                text = value,
                style =
                    RepFlowNumericTextStyle.copy(
                        fontSize = sizing.valueFontSize,
                        lineHeight = sizing.valueLineHeight,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        StepperButton(
            icon = RepFlowIcons.plus,
            contentDescription = incrementContentDescription,
            sizing = sizing,
            onClick = onIncrement,
        )
    }
}

/**
 * The stepper `6b` specifies end to end: −/+ move [value] by [step] inside
 * [[minValue], [maxValue]], and tapping the value opens [RepFlowNumericKeypad]
 * titled [keypadTitle]; a confirmed entry is clamped into the same range.
 *
 * Decimal arithmetic is exact ([BigDecimal]), so 2.5kg steps never drift into
 * `82.49999`. A null [value] - nothing entered yet - renders [emptyText] and
 * steps from [minValue].
 *
 * @param allowDecimal whether the keypad offers a working `.` key: true for a
 *   load, false for reps or seconds.
 */
@Composable
fun RepFlowStepper(
    value: BigDecimal?,
    onValueChange: (BigDecimal) -> Unit,
    step: BigDecimal,
    keypadTitle: String,
    decrementContentDescription: String,
    incrementContentDescription: String,
    modifier: Modifier = Modifier,
    minValue: BigDecimal = BigDecimal.ZERO,
    maxValue: BigDecimal? = null,
    allowDecimal: Boolean = true,
    emptyText: String = "0",
    format: (BigDecimal) -> String = RepFlowStepperMath::format,
    sizing: RepFlowStepperSizing = RepFlowStepperDefaults.compact,
) {
    var keypadOpen by rememberSaveable { mutableStateOf(false) }
    RepFlowStepper(
        value = value?.let(format) ?: emptyText,
        onDecrement = { onValueChange(RepFlowStepperMath.step(value, step, -1, minValue, maxValue)) },
        onIncrement = { onValueChange(RepFlowStepperMath.step(value, step, 1, minValue, maxValue)) },
        decrementContentDescription = decrementContentDescription,
        incrementContentDescription = incrementContentDescription,
        modifier = modifier,
        sizing = sizing,
        onValueClick = { keypadOpen = true },
    )
    if (keypadOpen) {
        RepFlowNumericKeypad(
            title = keypadTitle,
            allowDecimal = allowDecimal,
            onDismissRequest = { keypadOpen = false },
            onConfirm = { entered ->
                keypadOpen = false
                onValueChange(RepFlowStepperMath.coerce(entered, minValue, maxValue))
            },
        )
    }
}

/**
 * The stepper's arithmetic, kept out of the composable so a plain-JVM test
 * can pin it.
 */
object RepFlowStepperMath {
    /**
     * [current] moved one [step] in [direction] (negative is down), clamped
     * into [[min], [max]]. Nothing entered yet steps from [min].
     */
    fun step(
        current: BigDecimal?,
        step: BigDecimal,
        direction: Int,
        min: BigDecimal,
        max: BigDecimal?,
    ): BigDecimal {
        val start = current ?: return coerce(if (direction > 0) min + step else min, min, max)
        val moved = if (direction > 0) start + step else start - step
        return coerce(moved, min, max)
    }

    fun coerce(
        value: BigDecimal,
        min: BigDecimal,
        max: BigDecimal?,
    ): BigDecimal =
        when {
            value < min -> min
            max != null && value > max -> max
            else -> value
        }

    /** `82.5`, `80`, `0.25` - never `80.0` or `8.25E+1`. */
    fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
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
