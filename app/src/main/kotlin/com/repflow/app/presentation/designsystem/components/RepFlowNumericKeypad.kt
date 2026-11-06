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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.repflow.app.R
import com.repflow.app.presentation.designsystem.RepFlowColor
import com.repflow.app.presentation.designsystem.RepFlowNumericTextStyle
import com.repflow.app.presentation.designsystem.isDarkColorScheme
import com.repflow.app.presentation.designsystem.repFlowSecondaryTextColor
import java.math.BigDecimal

/*
 * The number pad `50h` draws, with its m:ss form from `10l`. It opens on the
 * current value, selected, so the first key replaces it, and `Set` confirms.
 * The header is the boards': the title at the left, the value centred at 40 in
 * a tinted ring while it is selected, and one line under it. A whole-number
 * pad greys its decimal key, and an m:ss pad has `00` in that cell.
 *
 * Kept from `1a` (`RepFlow.dc.html:2826-2843`), where the boards draw otherwise:
 * keys 56 tall with a gap of 8, and Cancel + Set at 1 : 2 in one row. The limit
 * line `50h` draws for an out-of-range value isn't built: the callers clamp.
 */

/** The selected value's tint and ring (`50h`'s `selBg` and `selBd`). */
@Immutable
data class RepFlowKeypadSelectedColors(
    val fill: Color,
    val ring: Color,
)

private const val KEYPAD_SELECTED_FILL_ALPHA_DARK = 0.18f
private const val KEYPAD_SELECTED_FILL_ALPHA_LIGHT = 0.14f

object RepFlowKeypadDefaults {
    val keyMinHeight = 56.dp
    val keyShape = RoundedCornerShape(10.dp)
    val keyGap = 8.dp
    val keyFontSize = 20.sp
    val titleFontSize = 18.sp
    val titleLineHeight = 24.sp
    val valueFontSize = 40.sp

    /** `line-height:1.2` on the 40 value. */
    val valueLineHeight = 48.sp
    val valueShape = RoundedCornerShape(10.dp)
    val borderWidth = 1.dp

    /** `padding:2px 14px` inside the ring. */
    val valueHorizontalPadding = 14.dp
    val valueVerticalPadding = 2.dp

    /** `margin:12px 0 2px` around the value. */
    val valueTopPadding = 12.dp
    val valueBottomPadding = 2.dp

    /** `opacity:.35` on a whole-number pad's decimal key. */
    const val DISABLED_KEY_ALPHA = 0.35f

    /** `padding:10px 12px 16px` inside the sheet; the sheet adds the 16 below. */
    val horizontalPadding = 12.dp

    /** `padding:0 6px` around the header. */
    val headerHorizontalPadding = 6.dp

    /** `margin:12px 0` between the header and the keys. */
    val headerBottomPadding = 12.dp

    /** `margin-top:10px` above Cancel/Set. */
    val actionsTopPadding = 10.dp

    /**
     * `primary` at the board's own opacity, with a `primary` ring. These aren't
     * [repFlowSelectedPillColors], whose light fill is transparent: a fill alone
     * would show nothing there.
     */
    fun selectedColors(scheme: ColorScheme): RepFlowKeypadSelectedColors =
        RepFlowKeypadSelectedColors(
            fill =
                scheme.primary.copy(
                    alpha = if (isDarkColorScheme(scheme)) KEYPAD_SELECTED_FILL_ALPHA_DARK else KEYPAD_SELECTED_FILL_ALPHA_LIGHT,
                ),
            ring = scheme.primary,
        )
}

/**
 * What has been typed, and whether it is still the opening value waiting to be
 * replaced. On an m:ss pad [text] holds only the digits (`130` for `1:30`).
 */
@Immutable
data class RepFlowKeypadEntry(
    val text: String,
    val selected: Boolean = false,
)

/**
 * The keypad's input rules, kept out of the composable so a plain-JVM test can
 * pin them. Digits and the six-character limit are the prototype's
 * (`RepFlow.dc.html:4616-4630`); opening selected is `50h`'s, and digits that
 * fill from the right like a clock are `10l`'s.
 */
object RepFlowKeypadInput {
    const val BACKSPACE = "⌫"
    const val DECIMAL = "."
    const val DOUBLE_ZERO = "00"

    /** `(st.kpText + k).slice(0, 6)`: at most six characters. */
    const val MAX_LENGTH = 6

    /** Five digits on an m:ss pad, so `120:00` fits. */
    const val MAX_DURATION_DIGITS = 5

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_DIGITS = 2
    private const val EMPTY_DECIMAL = "0."

    /** The design's own key order, row by row; an m:ss pad has `00` in the `.` cell. */
    fun keys(durationEntry: Boolean = false): List<String> =
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", if (durationEntry) DOUBLE_ZERO else DECIMAL, "0", BACKSPACE)

    /**
     * The entry a pad opens on: nothing for a null [initialValue], otherwise the
     * value, selected. A seed is never cut to a typing limit, so `Set` straight
     * away confirms the same value. On an m:ss pad [initialValue] is seconds, and
     * 90 seeds the digits `130`; zero seeds no digits, which reads `0:00`.
     */
    fun seed(
        initialValue: BigDecimal?,
        durationEntry: Boolean,
    ): RepFlowKeypadEntry =
        when {
            initialValue == null -> RepFlowKeypadEntry(text = "")
            durationEntry -> RepFlowKeypadEntry(text = durationDigits(initialValue.toLong()), selected = true)
            else -> RepFlowKeypadEntry(text = RepFlowStepperMath.format(initialValue), selected = true)
        }

    /**
     * [entry] after [key] is pressed. Backspace clears a selected value and
     * otherwise drops the last character. Any other key that does something
     * replaces a selected value; a key that does nothing leaves it selected.
     */
    fun press(
        entry: RepFlowKeypadEntry,
        key: String,
        allowDecimal: Boolean,
        durationEntry: Boolean = false,
    ): RepFlowKeypadEntry {
        val typed = if (entry.selected) "" else entry.text
        return when {
            key == BACKSPACE -> RepFlowKeypadEntry(text = typed.dropLast(1))
            durationEntry -> pressDuration(entry, typed, key)
            key != DECIMAL -> RepFlowKeypadEntry(text = (typed + key).take(MAX_LENGTH))
            !allowDecimal || DECIMAL in typed -> entry
            typed.isEmpty() -> RepFlowKeypadEntry(text = EMPTY_DECIMAL)
            else -> RepFlowKeypadEntry(text = (typed + key).take(MAX_LENGTH))
        }
    }

    /**
     * The confirmed value, or null when nothing parseable was typed (Set then
     * acts as Cancel). On an m:ss pad it is minutes × 60 + seconds, so `0:90`
     * confirms 90.
     */
    fun parse(
        text: String,
        durationEntry: Boolean = false,
    ): BigDecimal? =
        if (durationEntry && text.isNotEmpty()) {
            val minutes = text.dropLast(SECONDS_DIGITS).ifEmpty { "0" }.toBigDecimal()
            minutes * SECONDS_PER_MINUTE.toBigDecimal() + text.takeLast(SECONDS_DIGITS).toBigDecimal()
        } else {
            text.toBigDecimalOrNull()
        }

    /** What the header shows: the typed text or `0`, or on an m:ss pad the clock, `0:00` when empty. */
    fun display(
        text: String,
        durationEntry: Boolean = false,
    ): String =
        if (durationEntry) {
            val digits = text.padStart(SECONDS_DIGITS + 1, '0')
            "${digits.dropLast(SECONDS_DIGITS)}:${digits.takeLast(SECONDS_DIGITS)}"
        } else {
            text.ifEmpty { "0" }
        }

    private fun pressDuration(
        entry: RepFlowKeypadEntry,
        typed: String,
        key: String,
    ): RepFlowKeypadEntry =
        if (key == DECIMAL) {
            entry
        } else {
            RepFlowKeypadEntry(text = (typed + key).trimStart('0').take(MAX_DURATION_DIGITS))
        }

    private fun durationDigits(seconds: Long): String {
        val remainder = (seconds % SECONDS_PER_MINUTE).toString().padStart(SECONDS_DIGITS, '0')
        return "${seconds / SECONDS_PER_MINUTE}$remainder".trimStart('0')
    }
}

/**
 * The keypad sheet. It scrolls, because the header would otherwise cut `Set` off
 * in landscape and at font scale 2.0.
 *
 * @param initialValue the value it opens on, selected; null opens empty. On an
 *   m:ss pad this is seconds.
 * @param durationEntry an m:ss pad (`10l`): digits fill from the right like a
 *   clock, the decimal key is `00`, and [onConfirm] receives whole seconds.
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
    initialValue: BigDecimal? = null,
    durationEntry: Boolean = false,
    cancelLabel: String = stringResource(R.string.repflow_keypad_cancel),
    confirmLabel: String = stringResource(R.string.repflow_keypad_confirm),
    backspaceContentDescription: String = stringResource(R.string.repflow_keypad_backspace_content_description),
    clearContentDescription: String = stringResource(R.string.repflow_keypad_clear_content_description),
) {
    val opening = remember { RepFlowKeypadInput.seed(initialValue, durationEntry) }
    var text by rememberSaveable { mutableStateOf(opening.text) }
    var selected by rememberSaveable { mutableStateOf(opening.selected) }
    RepFlowSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = RepFlowKeypadDefaults.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(RepFlowKeypadDefaults.keyGap),
        ) {
            KeypadHeader(
                title = title,
                value = RepFlowKeypadInput.display(text, durationEntry),
                selected = selected,
                caption =
                    when {
                        durationEntry -> stringResource(R.string.repflow_keypad_duration_caption)
                        !allowDecimal -> stringResource(R.string.repflow_keypad_whole_numbers_caption)
                        else -> stringResource(R.string.repflow_keypad_selected_caption)
                    },
                captionShown = durationEntry || !allowDecimal || selected,
            )
            RepFlowKeypadInput.keys(durationEntry).chunked(KEYS_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(RepFlowKeypadDefaults.keyGap)) {
                    row.forEach { key ->
                        KeypadKey(
                            key = key,
                            contentDescription =
                                when {
                                    key != RepFlowKeypadInput.BACKSPACE -> null
                                    selected -> clearContentDescription
                                    else -> backspaceContentDescription
                                },
                            enabled = key != RepFlowKeypadInput.DECIMAL || allowDecimal,
                            onClick = {
                                val entry = RepFlowKeypadEntry(text, selected)
                                val next = RepFlowKeypadInput.press(entry, key, allowDecimal, durationEntry)
                                text = next.text
                                selected = next.selected
                            },
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
                        val parsed = RepFlowKeypadInput.parse(text, durationEntry)
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
    selected: Boolean,
    caption: String,
    captionShown: Boolean,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = RepFlowKeypadDefaults.headerHorizontalPadding,
                    end = RepFlowKeypadDefaults.headerHorizontalPadding,
                    bottom = RepFlowKeypadDefaults.headerBottomPadding - RepFlowKeypadDefaults.keyGap,
                ),
    ) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.titleMedium.copy(
                    fontSize = RepFlowKeypadDefaults.titleFontSize,
                    lineHeight = RepFlowKeypadDefaults.titleLineHeight,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        KeypadValue(
            value = value,
            selected = selected,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(
                        top = RepFlowKeypadDefaults.valueTopPadding,
                        bottom = RepFlowKeypadDefaults.valueBottomPadding,
                    ),
        )
        // A hidden caption keeps its height, so the keys don't move when it goes.
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = repFlowSecondaryTextColor(MaterialTheme.colorScheme),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(if (captionShown) Modifier else Modifier.alpha(0f).clearAndSetSemantics {}),
        )
    }
}

@Composable
private fun KeypadValue(
    value: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RepFlowKeypadDefaults.valueShape
    val colors = RepFlowKeypadDefaults.selectedColors(MaterialTheme.colorScheme)
    Text(
        text = value,
        style =
            RepFlowNumericTextStyle.copy(
                fontSize = RepFlowKeypadDefaults.valueFontSize,
                lineHeight = RepFlowKeypadDefaults.valueLineHeight,
            ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier =
            modifier
                .then(
                    if (selected) {
                        Modifier
                            .background(color = colors.fill, shape = shape)
                            .border(BorderStroke(RepFlowKeypadDefaults.borderWidth, colors.ring), shape)
                    } else {
                        Modifier
                    },
                ).padding(
                    horizontal = RepFlowKeypadDefaults.valueHorizontalPadding,
                    vertical = RepFlowKeypadDefaults.valueVerticalPadding,
                ).semantics {
                    liveRegion = LiveRegionMode.Polite
                    // Only while selected: a false value would read "not selected" with every digit.
                    if (selected) this.selected = true
                },
    )
}

@Composable
private fun KeypadKey(
    key: String,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RepFlowKeypadDefaults.keyShape
    Box(
        modifier =
            modifier
                .heightIn(min = RepFlowKeypadDefaults.keyMinHeight)
                .alpha(if (enabled) 1f else RepFlowKeypadDefaults.DISABLED_KEY_ALPHA)
                .clip(shape)
                .background(color = RepFlowColor.control, shape = shape)
                .border(BorderStroke(RepFlowKeypadDefaults.borderWidth, RepFlowColor.hairline), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
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
