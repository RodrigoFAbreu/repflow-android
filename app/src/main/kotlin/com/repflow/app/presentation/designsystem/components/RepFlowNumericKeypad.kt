package com.repflow.app.presentation.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal

/*
 * The numeric keypad `6b` makes normative ("The value itself is a button -
 * it opens the keypad"), drawn in `1a` (`RepFlow.dc.html:2826-2843`) with its
 * key logic in the prototype's script (`:4616-4630`): a sheet with the
 * field's title and the typed value (30/500 tabular), a 3-column grid of
 * `1 2 3 4 5 6 7 8 9 . 0 ⌫` keys 56 tall on `control` with a hairline, and
 * Cancel + Set at 1 : 2.
 */

object RepFlowKeypadDefaults {
    val keyMinHeight = 56.dp
    val keyShape = RoundedCornerShape(10.dp)
    val keyGap = 8.dp
    val keyFontSize = 20.sp
    val valueFontSize = 30.sp
    val borderWidth = 1.dp

    /** `padding:10px 12px 16px` inside the sheet; the sheet adds the 16 below. */
    val horizontalPadding = 12.dp

    /** `padding:0 6px 10px` around the title/value line. */
    val headerHorizontalPadding = 6.dp
    val headerBottomPadding = 10.dp

    /** `margin-top:10px` above Cancel/Set. */
    val actionsTopPadding = 10.dp
}

/**
 * The keypad's input rules, exactly as the prototype applies them, kept out of
 * the composable so a plain-JVM test can pin them.
 */
object RepFlowKeypadInput {
    const val BACKSPACE = "⌫"
    const val DECIMAL = "."

    /** The design's own key order, row by row. */
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", DECIMAL, "0", BACKSPACE)

    /** `(st.kpText + k).slice(0, 6)`: at most six characters. */
    const val MAX_LENGTH = 6

    /**
     * [text] after [key] is pressed: backspace drops the last character; a
     * second `.`, or any `.` when [allowDecimal] is false, is ignored; the
     * result never exceeds [MAX_LENGTH].
     */
    fun press(
        text: String,
        key: String,
        allowDecimal: Boolean,
    ): String =
        when {
            key == BACKSPACE -> text.dropLast(1)
            key == DECIMAL && (!allowDecimal || DECIMAL in text) -> text
            else -> (text + key).take(MAX_LENGTH)
        }

    /** The confirmed value, or null when nothing parseable was typed (Set then acts as Cancel). */
    fun parse(text: String): BigDecimal? = text.toBigDecimalOrNull()

    /** What the header shows: the typed text, or `0` before the first key. */
    fun display(text: String): String = text.ifEmpty { "0" }
}

/**
 * The keypad sheet. Typing starts from empty, as the design's does: the value
 * being replaced is still on the stepper behind the sheet.
 *
 * @param onConfirm receives the parsed value; Set on an empty or unparseable
 *   entry dismisses instead, as the prototype's `kpDone` does.
 */
@Composable
fun RepFlowNumericKeypad(
    title: String,
    allowDecimal: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (BigDecimal) -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = stringResource(R.string.repflow_keypad_cancel),
    confirmLabel: String = stringResource(R.string.repflow_keypad_confirm),
    backspaceContentDescription: String = stringResource(R.string.repflow_keypad_backspace_content_description),
) {
    var text by rememberSaveable { mutableStateOf("") }
    RepFlowSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = RepFlowKeypadDefaults.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(RepFlowKeypadDefaults.keyGap),
        ) {
            KeypadHeader(title = title, value = RepFlowKeypadInput.display(text))
            RepFlowKeypadInput.keys.chunked(KEYS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(RepFlowKeypadDefaults.keyGap)) {
                    row.forEach { key ->
                        KeypadKey(
                            key = key,
                            contentDescription = if (key == RepFlowKeypadInput.BACKSPACE) backspaceContentDescription else null,
                            onClick = { text = RepFlowKeypadInput.press(text, key, allowDecimal) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = RepFlowKeypadDefaults.actionsTopPadding - RepFlowKeypadDefaults.keyGap),
                horizontalArrangement = Arrangement.spacedBy(RepFlowKeypadDefaults.keyGap),
            ) {
                RepFlowNeutralOutlineButton(
                    text = cancelLabel,
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f).heightIn(min = RepFlowButtonDefaults.primaryMinHeight),
                )
                RepFlowPrimaryButton(
                    text = confirmLabel,
                    onClick = {
                        val parsed = RepFlowKeypadInput.parse(text)
                        if (parsed == null) onDismissRequest() else onConfirm(parsed)
                    },
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

private const val KEYS_PER_ROW = 3

@Composable
private fun KeypadHeader(
    title: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = RepFlowKeypadDefaults.headerHorizontalPadding,
                    end = RepFlowKeypadDefaults.headerHorizontalPadding,
                    bottom = RepFlowKeypadDefaults.headerBottomPadding - RepFlowKeypadDefaults.keyGap,
                ),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
        )
        Text(
            text = value,
            style = RepFlowNumericTextStyle.copy(fontSize = RepFlowKeypadDefaults.valueFontSize),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun KeypadKey(
    key: String,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RepFlowKeypadDefaults.keyShape
    Box(
        modifier =
            modifier
                .heightIn(min = RepFlowKeypadDefaults.keyMinHeight)
                .clip(shape)
                .background(color = RepFlowColor.control, shape = shape)
                .border(BorderStroke(RepFlowKeypadDefaults.borderWidth, RepFlowColor.hairline), shape)
                .clickable(role = Role.Button, onClick = onClick)
                .then(
                    if (contentDescription == null) {
                        Modifier
                    } else {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key,
            style = RepFlowNumericTextStyle.copy(fontSize = RepFlowKeypadDefaults.keyFontSize),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
